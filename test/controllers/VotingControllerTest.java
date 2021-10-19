package controllers;

import clients.VotingTestClient;
import com.typesafe.config.Config;
import data.entities.JpaVoting;
import data.entities.JpaVotingIssuer;
import devote.blockchain.mockblockchain.MockBlockchainIssuerAccount;
import dto.CreateVotingRequest;
import io.ebean.Ebean;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import play.libs.Json;
import play.mvc.Result;
import rules.RuleChainForTests;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static extractors.GenericDataFromResult.statusOf;
import static extractors.VotingResponseFromResult.idOf;
import static extractors.VotingResponseFromResult.networkOf;
import static junit.framework.TestCase.assertTrue;
import static matchers.ResultHasLocationHeader.hasLocationHeader;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static play.mvc.Http.HeaderNames.LOCATION;
import static play.mvc.Http.Status.CREATED;
import static play.mvc.Http.Status.OK;

public class VotingControllerTest {
    private final RuleChainForTests ruleChainForTests = new RuleChainForTests();

    @Rule
    public RuleChain chain = ruleChainForTests.getRuleChain();

    private VotingTestClient client;
    private Config config;

    @Before
    public void setup() {
        client = new VotingTestClient(ruleChainForTests.getApplication());
        config = ruleChainForTests.getApplication().config();
    }

    @Test
    public void testCreate() throws IOException {
        // Given
        CreateVotingRequest createVotingRequest = createValidVotingRequest();

        // When
        Result result = client.createVoting(createVotingRequest);

        // Then
        assertThat(statusOf(result), equalTo(CREATED));
        assertThat(result, hasLocationHeader());

        String locationUrl = result.headers().get(LOCATION);
        Result getByLocationResult = client.byLocation(locationUrl);

        assertThat(statusOf(getByLocationResult), equalTo(OK));
        assertThat(idOf(getByLocationResult), greaterThan(0L));
        assertThat(networkOf(getByLocationResult), equalTo("mockblockchain"));
        assertIssuerAccountsCreatedOnBlockchain(idOf(result));
    }

    private static CreateVotingRequest createValidVotingRequest() throws IOException {
        InputStream sampleVotingIS = VotingControllerTest.class
                .getClassLoader().getResourceAsStream("voting-request-base.json");
        CreateVotingRequest votingRequest = Json.mapper().readValue(sampleVotingIS, CreateVotingRequest.class);
        votingRequest.setNetwork("mockblockchain");

        return votingRequest;
    }

    private static void assertIssuerAccountsCreatedOnBlockchain(Long votingId) {
        List<String> accounts = issuerAccountsOf(votingId);
        assertThat(accounts, not(empty()));
        accounts.forEach(VotingControllerTest::assertIssuerAccountCreatedOnBlockchain);
    }

    private static void assertIssuerAccountCreatedOnBlockchain(String account) {
        assertTrue(MockBlockchainIssuerAccount.isCreated(account));
    }

    private static List<String> issuerAccountsOf(Long votingId) {
        JpaVoting entity = Ebean.find(JpaVoting.class, votingId);
        return entity.getIssuers().stream()
                .map(JpaVotingIssuer::getAccountSecret)
                .collect(Collectors.toList());
    }

}
