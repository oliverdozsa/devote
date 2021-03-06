package components.controllers;

import asserts.IpfsAsserts;
import com.fasterxml.jackson.databind.JsonNode;
import components.clients.VotingTestClient;
import io.ipfs.api.IPFS;
import ipfs.api.IpfsApi;
import security.UserInfoCollectorForTest;
import security.jwtverification.JwtVerification;
import security.jwtverification.JwtVerificationForTests;
import services.commissionsubs.userinfo.UserInfoCollector;
import units.ipfs.api.imp.MockIpfsApi;
import units.ipfs.api.imp.MockIpfsProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Result;
import requests.CreateVotingRequest;
import rules.RuleChainForTests;
import services.Base62Conversions;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static asserts.BlockchainAsserts.*;
import static asserts.DbAsserts.*;
import static components.controllers.VotingRequestMaker.createValidVotingRequest;
import static components.extractors.GenericDataFromResult.jsonOf;
import static components.extractors.GenericDataFromResult.statusOf;
import static components.extractors.VotingResponseFromResult.idOf;
import static components.extractors.VotingResponseFromResult.networkOf;
import static matchers.ResultHasHeader.hasLocationHeader;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;
import static play.inject.Bindings.bind;
import static play.mvc.Http.HeaderNames.LOCATION;
import static play.mvc.Http.Status.*;

public class VotingControllerTest {
    private final RuleChainForTests ruleChainForTests;

    @Rule
    public RuleChain chain;

    private VotingTestClient client;
    private IpfsAsserts ipfsAsserts;

    public VotingControllerTest() {
        GuiceApplicationBuilder applicationBuilder = new GuiceApplicationBuilder()
                .overrides(bind(IpfsApi.class).to(MockIpfsApi.class))
                .overrides(bind(IPFS.class).toProvider(MockIpfsProvider.class))
                .overrides(bind(UserInfoCollector.class).to(UserInfoCollectorForTest.class))
                .overrides((bind(JwtVerification.class).to(JwtVerificationForTests.class)));

        ruleChainForTests = new RuleChainForTests(applicationBuilder);
        chain = ruleChainForTests.getRuleChain();
    }

    @Before
    public void setup() {
        client = new VotingTestClient(ruleChainForTests.getApplication());
        ipfsAsserts = new IpfsAsserts(ruleChainForTests.getApplication());
    }

    @Test
    public void testCreate() throws InterruptedException, IOException {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("john@mail.com", "doe@where.de", "some@one.com"));

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);

        // Wait for voting init & channel task
        Thread.sleep(30 * 1000);
        Result getByLocationResult = client.byLocation(locationUrl);

        assertThat(statusOf(getByLocationResult), equalTo(OK));

        Long votingId = idOf(getByLocationResult);
        assertThat(votingId, greaterThan(0L));
        assertThat(networkOf(getByLocationResult), equalTo("mockblockchain"));
        assertIssuerAccountsCreatedOnBlockchain(votingId);
        assertDistributionAndBallotAccountsCreatedOnBlockchain(votingId);
        assertVoteTokenIsSavedInDb(votingId);
        assertVotingEncryptionSavedInDb(votingId);
        assertVotingStartEndDateSavedInDb(votingId);
        assertAuthorizationEmailsSavedInDb(votingId, "john@mail.com", "doe@where.de", "some@one.com");
        assertPollSavedInDb(votingId, createVotingRequest.getPolls());
        ipfsAsserts.assertVotingSavedToIpfs(votingId);

        assertChannelAccountsCreatedOnBlockchain(votingId);
        assertChannelProgressCompletedFor(votingId);
    }

    @Test
    public void testBadRequestReturnedWhenCreatingVoteWithInvalidRequest() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("john@mail.com", "doe@where.de", "some@one.com"));

        createVotingRequest.setStartDate(Instant.now());
        createVotingRequest.setEndDate(Instant.now().minus(Duration.ofDays(1)));

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(BAD_REQUEST));
    }

    @Test
    public void testWithInvalidOptions() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("john@mail.com", "doe@where.de", "some@one.com"));

        createVotingRequest.getPolls().get(0).getOptions().get(1).setCode(1);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(BAD_REQUEST));
    }

    @Test
    public void testSingle_NonExistingId() {
        // Given
        String nonExistingVotingId = Base62Conversions.encode(42L);

        // When
        Result result = client.single(nonExistingVotingId);

        // Then
        assertThat(statusOf(result), equalTo(NOT_FOUND));
    }

    @Test
    public void testSingle_IdNotEncodedInBase62() {
        // Given
        String wrongEncoding = "+=";

        // When
        Result result = client.single(wrongEncoding);

        // Then
        assertThat(statusOf(result), equalTo(BAD_REQUEST));
    }

    @Test
    public void testCreateThenGetImmediately() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("john@mail.com", "doe@where.de", "some@one.com"));

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);
        Result getByLocationResult = client.byLocation(locationUrl);

        assertThat(statusOf(getByLocationResult), equalTo(OK));

        JsonNode votingResponseJson = jsonOf(getByLocationResult);
        assertTrue(votingResponseJson.get("distributionAccountId").isNull());
        assertTrue(votingResponseJson.get("ballotAccountId").isNull());
        assertTrue(votingResponseJson.get("issuerAccountId").isNull());
    }

    @Test
    public void testSinglePrivateWithAuth_UserIsTheVoteCaller() throws InterruptedException {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("john@mail.com", "doe@where.de", "some@one.com"));
        createVotingRequest.setVisibility(CreateVotingRequest.Visibility.PRIVATE);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);
        Result getByLocationResult = client.byLocation(locationUrl, "Alice", new String[]{"voter", "vote-caller"});

        assertThat(statusOf(getByLocationResult), equalTo(OK));
    }

    @Test
    public void testSinglePrivateWithAuth_UserIsNotParticipant() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("john@mail.com", "doe@where.de", "some@one.com"));
        createVotingRequest.setVisibility(CreateVotingRequest.Visibility.PRIVATE);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);
        Result getByLocationResult = client.byLocation(locationUrl, "Charlie", new String[]{"voter", "vote-caller"});

        assertThat(statusOf(getByLocationResult), equalTo(FORBIDDEN));
    }

    @Test
    public void testSinglePrivateWithAuth_UserIsParticipant() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("bob@mail.com", "doe@where.de", "some@one.com"));
        createVotingRequest.setVisibility(CreateVotingRequest.Visibility.PRIVATE);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);
        Result getByLocationResult = client.byLocation(locationUrl, "Bob", new String[]{"voter", "vote-caller"});

        assertThat(statusOf(getByLocationResult), equalTo(OK));
    }

    @Test
    public void testSinglePrivateWithAuth_UserHasNoProperRole() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("bob@mail.com", "doe@where.de", "some@one.com"));
        createVotingRequest.setVisibility(CreateVotingRequest.Visibility.PRIVATE);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);
        Result getByLocationResult = client.byLocation(locationUrl, "Bob", new String[]{"cook", "driver"});

        assertThat(statusOf(getByLocationResult), equalTo(FORBIDDEN));
    }

    @Test
    public void testSingle_Unlisted() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("bob@mail.com", "doe@where.de", "some@one.com"));
        createVotingRequest.setVisibility(CreateVotingRequest.Visibility.UNLISTED);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);
        Result getByLocationResult = client.byLocation(locationUrl, "Charlie", new String[]{"cook", "driver"});

        assertThat(statusOf(getByLocationResult), equalTo(OK));
    }

    @Test
    public void testCreateOnTestNet() throws InterruptedException {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("bob@mail.com", "doe@where.de", "some@one.com"));
        createVotingRequest.setUseTestnet(true);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(CREATED));

        // Wait for voting init & channel task
        Thread.sleep(30 * 1000);

        assertChannelGeneratorsCreatedOnTestMockBlockchainNetwork();
    }

    @Test
    public void testVotesCapTooBig() {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();
        createVotingRequest.setAuthorization(CreateVotingRequest.Authorization.EMAILS);
        createVotingRequest.setAuthorizationEmailOptions(Arrays.asList("bob@mail.com", "doe@where.de", "some@one.com"));
        createVotingRequest.setVotesCap(221L);

        // When
        Result result = client.createVoting(createVotingRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(BAD_REQUEST));
    }
}
