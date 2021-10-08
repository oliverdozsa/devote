package controllers;

import clients.VotingTestClient;
import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import play.mvc.Result;
import rules.RuleChainForTests;

import java.io.IOException;

import static extractors.GenericDataFromResult.statusOf;
import static matchers.ResultHasLocationHeader.hasLocationHeader;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
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
        // TODO

        // Given
        // TODO

        // When
        Result result = client.createVoting();

        // Then
        assertThat(statusOf(result), equalTo(OK));
    }

}
