package components.controllers;

import components.clients.CommissionTestClient;
import components.clients.VotingTestClient;
import controllers.routes;
import io.ipfs.api.IPFS;
import ipfs.api.IpfsApi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import requests.CommissionAccountCreationRequest;
import requests.CommissionInitRequest;
import rules.RuleChainForTests;
import security.UserInfoCollectorForTest;
import security.jwtverification.JwtVerificationForTests;
import security.jwtverification.JwtVerification;
import services.commissionsubs.userinfo.UserInfoCollector;
import units.ipfs.api.imp.MockIpfsApi;
import units.ipfs.api.imp.MockIpfsProvider;
import utils.JwtTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static asserts.DbAsserts.assertThatTransactionIsStoredFor;
import static components.extractors.CommissionResponseFromResult.*;
import static components.extractors.GenericDataFromResult.statusOf;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.isEmptyString;
import static play.inject.Bindings.bind;
import static play.mvc.Http.HeaderNames.CONTENT_TYPE;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.POST;
import static play.test.Helpers.route;
import static utils.JwtTestUtils.addJwtTokenTo;

public class CommissionControllerTest {
    @Rule
    public RuleChain chain;

    private final RuleChainForTests ruleChainForTests;

    private CommissionTestClient testClient;
    private VotingTestClient votingTestClient;
    private VoteCreationUtils voteCreationUtils;

    public CommissionControllerTest() {
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
        testClient = new CommissionTestClient(ruleChainForTests.getApplication());
        votingTestClient = new VotingTestClient(ruleChainForTests.getApplication());
        voteCreationUtils = new VoteCreationUtils(testClient, votingTestClient);
        UserInfoCollectorForTest.setReturnValue(Json.parse("{\"sub\": \"Alice\", \"email\": \"alice@mail.com\", \"email_verified\": true}"));
    }

    @Test
    public void testInit() throws InterruptedException {
        // Given
        String votingId = voteCreationUtils.createValidVotingWithWaitingForFullInit();
        CommissionInitRequest initRequest = new CommissionInitRequest();
        initRequest.setVotingId(votingId);

        // When
        Result result = testClient.init(initRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(OK));

        assertThat(publicKeyOf(result), notNullValue());
    }

    @Test
    public void testInitVotingIsNotInitializedProperly() {
        // Given
        String votingId = voteCreationUtils.createValidVoting();
        CommissionInitRequest initRequest = new CommissionInitRequest();
        initRequest.setVotingId(votingId);

        // When
        Result result = testClient.init(initRequest, "Alice");

        // Then
        assertThat(statusOf(result), equalTo(FORBIDDEN));
    }

    @Test
    public void testJwtNotPresent() {
        // Given
        CommissionInitRequest initRequest = new CommissionInitRequest();
        initRequest.setVotingId("Some Voting");

        Http.RequestBuilder httpRequest = new Http.RequestBuilder()
                .method(POST)
                .bodyJson(Json.toJson(initRequest))
                .header(CONTENT_TYPE, Http.MimeTypes.JSON)
                .uri(routes.CommissionController.init().url());

        // When
        Result result = route(ruleChainForTests.getApplication(), httpRequest);

        // Then
        assertThat(statusOf(result), equalTo(FORBIDDEN));
    }

    @Test
    public void testJwtExpired() {
        // Given
        CommissionInitRequest initRequest = new CommissionInitRequest();
        initRequest.setVotingId("Some Voting");

        Http.RequestBuilder httpRequest = new Http.RequestBuilder()
                .method(POST)
                .bodyJson(Json.toJson(initRequest))
                .header(CONTENT_TYPE, Http.MimeTypes.JSON)
                .uri(routes.CommissionController.init().url());

        JwtTestUtils jwtTestUtils = new JwtTestUtils(ruleChainForTests.getApplication().config());
        Date expiresAt = Date.from(Instant.now().minus(5, ChronoUnit.SECONDS));
        String jwt = jwtTestUtils.createToken(expiresAt, "Some user");
        addJwtTokenTo(httpRequest, jwt);

        // When
        Result result = route(ruleChainForTests.getApplication(), httpRequest);

        // Then
        assertThat(statusOf(result), equalTo(FORBIDDEN));
    }

    @Test
    public void testSignOnEnvelope() throws InterruptedException {
        // Given
        VoteCreationUtils.InitData votingInitData = voteCreationUtils.initVotingFor("Alice");
        String message = voteCreationUtils.createMessage(votingInitData.votingId, "someAccountId");

        // When
        CommissionTestClient.SignOnEnvelopeResult result = testClient.signOnEnvelope(votingInitData.publicKey, "Alice", message, votingInitData.votingId);

        // Then
        assertThat(statusOf(result.http), equalTo(OK));
        assertThat(envelopeSignatureOf(result.http), notNullValue());
        assertThat(envelopeSignatureOf(result.http).length(), greaterThan(0));
    }

    @Test
    public void testDoubleEnvelope() throws InterruptedException {
        // Given
        VoteCreationUtils.InitData votingInitData = voteCreationUtils.initVotingFor("Alice");
        String message = voteCreationUtils.createMessage(votingInitData.votingId, "someAccountId");

        CommissionTestClient.SignOnEnvelopeResult result = testClient.signOnEnvelope(votingInitData.publicKey, "Alice", message, votingInitData.votingId);
        assertThat(statusOf(result.http), equalTo(OK));
        assertThat(envelopeSignatureOf(result.http), notNullValue());
        assertThat(envelopeSignatureOf(result.http).length(), greaterThan(0));

        String newMessage = voteCreationUtils.createMessage(votingInitData.votingId, "anotherAccountId");

        // When
        CommissionTestClient.SignOnEnvelopeResult newResult = testClient.signOnEnvelope(votingInitData.publicKey, "Alice", newMessage, votingInitData.votingId);

        // Then
        assertThat(statusOf(newResult.http), equalTo(FORBIDDEN));
    }

    @Test
    public void testInitFormError() {
        // Given
        Http.RequestBuilder httpRequest = new Http.RequestBuilder()
                .method(POST)
                .header(CONTENT_TYPE, Http.MimeTypes.JSON)
                .uri(routes.CommissionController.init().url());

        JwtTestUtils jwtTestUtils = new JwtTestUtils(ruleChainForTests.getApplication().config());
        String jwt = jwtTestUtils.createToken("Alice");
        addJwtTokenTo(httpRequest, jwt);

        // When
        Result result = route(ruleChainForTests.getApplication(), httpRequest);

        // Then
        assertThat(statusOf(result), equalTo(BAD_REQUEST));
    }

    @Test
    public void testSignEnvelopeFormError() {
        // Given
        Http.RequestBuilder httpRequest = new Http.RequestBuilder()
                .method(POST)
                .header(CONTENT_TYPE, Http.MimeTypes.JSON)
                .uri(routes.CommissionController.signEnvelope("42").url());

        JwtTestUtils jwtTestUtils = new JwtTestUtils(ruleChainForTests.getApplication().config());
        String jwt = jwtTestUtils.createToken("Alice");
        addJwtTokenTo(httpRequest, jwt);

        // When
        Result result = route(ruleChainForTests.getApplication(), httpRequest);

        // Then
        assertThat(statusOf(result), equalTo(BAD_REQUEST));
    }

    @Test
    public void testAccountCreationRequest() throws InterruptedException {
        // Given
        VoteCreationUtils.InitData votingInitData = voteCreationUtils.initVotingFor("Alice");
        Thread.sleep(15 * 1000); // So that some channel accounts are present.
        String message = voteCreationUtils.createMessage(votingInitData.votingId, "someAccountId");

        CommissionTestClient.SignOnEnvelopeResult result = testClient.signOnEnvelope(votingInitData.publicKey, "Alice", message, votingInitData.votingId);
        assertThat(statusOf(result.http), equalTo(OK));
        assertThat(envelopeSignatureOf(result.http), notNullValue());
        assertThat(envelopeSignatureOf(result.http).length(), greaterThan(0));

        // When
        String envelopeSignatureBase64 = envelopeSignatureOf(result.http);
        CommissionAccountCreationRequest accountCreationRequest = CommissionTestClient.createAccountCreationRequest(message, envelopeSignatureBase64, result.envelope);
        Result accountCreationRequestResult = testClient.requestAccountCreation(accountCreationRequest);

        // Then
        assertThat(statusOf(accountCreationRequestResult), equalTo(OK));
        assertThat(accountCreationTransactionOf(accountCreationRequestResult), notNullValue());
        assertThat(accountCreationTransactionOf(accountCreationRequestResult), not(isEmptyString()));
        assertThatTransactionIsStoredFor(accountCreationRequest.getRevealedSignatureBase64());
    }

    @Test
    public void testDoubleAccountCreationRequest() throws InterruptedException {
        // Given
        VoteCreationUtils.InitData votingInitData = voteCreationUtils.initVotingFor("Alice");
        Thread.sleep(15 * 1000); // So that some channel accounts are present.
        String message = voteCreationUtils.createMessage(votingInitData.votingId, "someAccountId");

        CommissionTestClient.SignOnEnvelopeResult result = testClient.signOnEnvelope(votingInitData.publicKey, "Alice", message, votingInitData.votingId);
        assertThat(statusOf(result.http), equalTo(OK));
        assertThat(envelopeSignatureOf(result.http), notNullValue());
        assertThat(envelopeSignatureOf(result.http).length(), greaterThan(0));

        String envelopeSignatureBase64 = envelopeSignatureOf(result.http);
        CommissionAccountCreationRequest accountCreationRequest = CommissionTestClient.createAccountCreationRequest(message, envelopeSignatureBase64, result.envelope);
        Result accountCreationRequestResult = testClient.requestAccountCreation(accountCreationRequest);

        assertThat(statusOf(accountCreationRequestResult), equalTo(OK));
        assertThat(accountCreationTransactionOf(accountCreationRequestResult), notNullValue());
        assertThat(accountCreationTransactionOf(accountCreationRequestResult), not(isEmptyString()));
        assertThatTransactionIsStoredFor(accountCreationRequest.getRevealedSignatureBase64());

        // When
        Result secondAccountCreationRequestResult = testClient.requestAccountCreation(accountCreationRequest);

        // Then
        assertThat(statusOf(secondAccountCreationRequestResult), equalTo(FORBIDDEN));
    }

    @Test
    public void testGetAccountCreationTransaction() throws InterruptedException {
        // Given
        VoteCreationUtils.InitData votingInitData = voteCreationUtils.initVotingFor("Alice");
        Thread.sleep(15 * 1000); // So that some channel accounts are present.
        String message = voteCreationUtils.createMessage(votingInitData.votingId, "someAccountId");

        CommissionTestClient.SignOnEnvelopeResult result = testClient.signOnEnvelope(votingInitData.publicKey, "Alice", message, votingInitData.votingId);
        assertThat(statusOf(result.http), equalTo(OK));
        assertThat(envelopeSignatureOf(result.http), notNullValue());
        assertThat(envelopeSignatureOf(result.http).length(), greaterThan(0));

        String envelopeSignatureBase64 = envelopeSignatureOf(result.http);
        CommissionAccountCreationRequest accountCreationRequest = CommissionTestClient.createAccountCreationRequest(message, envelopeSignatureBase64, result.envelope);
        Result accountCreationRequestResult = testClient.requestAccountCreation(accountCreationRequest);

        assertThat(statusOf(accountCreationRequestResult), equalTo(OK));
        assertThat(accountCreationTransactionOf(accountCreationRequestResult), notNullValue());
        assertThat(accountCreationTransactionOf(accountCreationRequestResult), not(isEmptyString()));
        assertThatTransactionIsStoredFor(accountCreationRequest.getRevealedSignatureBase64());

        // When
        Result transactionOfSignatureResult = testClient.transactionOfSignature(accountCreationRequest.getRevealedSignatureBase64());

        // Then
        assertThat(statusOf(transactionOfSignatureResult), equalTo(OK));
        assertThat(transactionOfSignature(transactionOfSignatureResult), notNullValue());
    }

    @Test
    public void testGetAccountCreationTransaction_NotCreatedBefore() {
        // Given
        String someRandomSignature = "844221";

        // When
        Result transactionOfSignatureResult = testClient.transactionOfSignature(someRandomSignature);

        // Then
        assertThat(statusOf(transactionOfSignatureResult), equalTo(NOT_FOUND));
    }

    @Test
    public void testGetEnvelopeSignatureForUserInVoting() throws InterruptedException {
        // Given
        VoteCreationUtils.InitData votingInitData = voteCreationUtils.initVotingFor("Alice");
        String message = voteCreationUtils.createMessage(votingInitData.votingId, "someAccountId");

        CommissionTestClient.SignOnEnvelopeResult result = testClient.signOnEnvelope(votingInitData.publicKey, "Alice", message, votingInitData.votingId);
        assertThat(statusOf(result.http), equalTo(OK));
        String envelopeSignatureOnSign = envelopeSignatureOf(result.http);

        assertThat(envelopeSignatureOnSign, notNullValue());
        assertThat(envelopeSignatureOf(result.http).length(), greaterThan(0));

        // When
        Result getEnvelopeSignatureResult = testClient.envelopeSignatureOf(votingInitData.votingId, "Alice");

        // Then
        assertThat(statusOf(getEnvelopeSignatureResult), equalTo(OK));
        assertThat(envelopeSignatureOf(getEnvelopeSignatureResult), notNullValue());

        String getEnvelopeSignature = envelopeSignatureOf(getEnvelopeSignatureResult);
        assertThat(getEnvelopeSignature, equalTo(envelopeSignatureOnSign));
    }

    @Test
    public void testGetEnvelopeSignatureForUserInVoting_NotSignedEnvelopeBefore() throws InterruptedException {
        // Given
        VoteCreationUtils.InitData votingInitData = voteCreationUtils.initVotingFor("Alice");

        // When
        Result getEnvelopeSignatureResult = testClient.envelopeSignatureOf(votingInitData.votingId, "Alice");

        // Then
        assertThat(statusOf(getEnvelopeSignatureResult), equalTo(NOT_FOUND));
    }
}
