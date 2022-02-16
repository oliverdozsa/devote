package components.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import components.clients.CommissionTestClient;
import components.clients.VotingTestClient;
import components.extractors.GenericDataFromResult;
import crypto.AesCtrCrypto;
import io.ipfs.api.IPFS;
import ipfs.api.IpfsApi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Result;
import rules.RuleChainForTests;
import services.Base62Conversions;
import units.ipfs.api.imp.MockIpfsApi;
import units.ipfs.api.imp.MockIpfsProvider;

import java.time.Instant;
import java.util.Base64;

import static components.extractors.CommissionResponseFromResult.encryptedOptionCodeOf;
import static components.extractors.GenericDataFromResult.statusOf;
import static components.extractors.VotingResponseFromResult.decryptionKeyOf;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;
import static play.inject.Bindings.bind;
import static play.mvc.Http.Status.*;

public class EncryptedVotingTest {
    @Rule
    public RuleChain chain;

    private final RuleChainForTests ruleChainForTests;

    private CommissionTestClient testClient;
    private VotingTestClient votingTestClient;
    private VoteCreationUtils voteCreationUtils;

    public EncryptedVotingTest() {
        GuiceApplicationBuilder applicationBuilder = new GuiceApplicationBuilder()
                .overrides(bind(IpfsApi.class).to(MockIpfsApi.class))
                .overrides(bind(IPFS.class).toProvider(MockIpfsProvider.class));

        ruleChainForTests = new RuleChainForTests(applicationBuilder);
        chain = ruleChainForTests.getRuleChain();
    }

    @Before
    public void setup() {
        testClient = new CommissionTestClient(ruleChainForTests.getApplication());
        votingTestClient = new VotingTestClient(ruleChainForTests.getApplication());
        voteCreationUtils = new VoteCreationUtils(testClient, votingTestClient);
    }

    @Test
    public void testEncryptOptionCodeForVoting() throws InterruptedException {
        // Given
        String votingId = voteCreationUtils.createValidVoting();

        // When
        int anOptionCode = 42;
        Result anEncryptedOptionResult = testClient.encryptOptionCode(votingId, anOptionCode);
        Result anotherEncryptedOptionResult = testClient.encryptOptionCode(votingId, anOptionCode);

        // Then
        assertThat(statusOf(anEncryptedOptionResult), equalTo(OK));
        assertThat(statusOf(anotherEncryptedOptionResult), equalTo(OK));

        String firstEncryptedOptionCode = encryptedOptionCodeOf(anEncryptedOptionResult);
        String secondEncryptedOptionCode = encryptedOptionCodeOf(anotherEncryptedOptionResult);

        assertThat(firstEncryptedOptionCode, notNullValue());
        assertThat(firstEncryptedOptionCode.length(), greaterThan(0));
        assertThat(secondEncryptedOptionCode, notNullValue());
        assertThat(secondEncryptedOptionCode.length(), greaterThan(0));
        assertThat(firstEncryptedOptionCode, not(equalTo(secondEncryptedOptionCode)));
    }

    @Test
    public void testEncryptOptionCodeForVoting_InvalidOptionCode() throws InterruptedException {
        // Given
        String votingId = voteCreationUtils.createValidVoting();

        // When
        int anOptionCode = 4200;
        Result anEncryptedOptionResult = testClient.encryptOptionCode(votingId, anOptionCode);

        // Then
        assertThat(statusOf(anEncryptedOptionResult), equalTo(BAD_REQUEST));

    }

    @Test
    public void testEncryptOptionCodeForVoting_VotingDoesNotExist() throws InterruptedException {
        // Given
        voteCreationUtils.createValidVoting();

        // When
        int anOptionCode = 42;
        Result anEncryptedOptionResult = testClient.encryptOptionCode(Base62Conversions.encode(42L), anOptionCode);

        // Then
        assertThat(statusOf(anEncryptedOptionResult), equalTo(NOT_FOUND));
    }

    @Test
    public void testEncryptOptionCodeForVoting_VotingIsNotEncrypted() throws InterruptedException {
        // Given
        String votingId = voteCreationUtils.createValidNotEncryptedVoting();

        // When
        int anOptionCode = 42;
        Result anEncryptedOptionResult = testClient.encryptOptionCode(votingId, anOptionCode);

        // Then
        assertThat(statusOf(anEncryptedOptionResult), equalTo(BAD_REQUEST));
    }

    @Test
    public void testEncryptOptionCodeEncryptedUntilExpires() throws InterruptedException {
        // Given
        String votingId = voteCreationUtils.createValidVotingEncryptedUntil(Instant.now().plusSeconds(9));

        // When
        int anOptionCode = 42;
        Result anEncryptedOptionResult = testClient.encryptOptionCode(votingId, anOptionCode);
        Result anotherEncryptedOptionResult = testClient.encryptOptionCode(votingId, anOptionCode);

        // Wait for expiration of encrypted until.
        Thread.sleep(11 * 1000);

        Result votingResult = votingTestClient.single(votingId);

        // Then
        assertThat(statusOf(anEncryptedOptionResult), equalTo(OK));
        assertThat(statusOf(anotherEncryptedOptionResult), equalTo(OK));
        assertThat(statusOf(votingResult), equalTo(OK));

        String firstEncryptedOptionCode = encryptedOptionCodeOf(anEncryptedOptionResult);
        String secondEncryptedOptionCode = encryptedOptionCodeOf(anotherEncryptedOptionResult);

        assertThat(firstEncryptedOptionCode, notNullValue());
        assertThat(firstEncryptedOptionCode.length(), greaterThan(0));
        assertThat(secondEncryptedOptionCode, notNullValue());
        assertThat(secondEncryptedOptionCode.length(), greaterThan(0));
        assertThat(firstEncryptedOptionCode, not(equalTo(secondEncryptedOptionCode)));

        String decryptionKey = decryptionKeyOf(votingResult);
        assertThat(decryptionKey, notNullValue());
        assertThat(decryptionKey.length(), greaterThan(0));

        byte[] decryptionKeyBytes = Base64.getDecoder().decode(decryptionKey);
        byte[] firstOptionCodeCipherBytes = Base64.getDecoder().decode(firstEncryptedOptionCode);
        byte[] secondOptionCodeCipherBytes = Base64.getDecoder().decode(secondEncryptedOptionCode);

        byte[] firstDecryptedBytes = AesCtrCrypto.decrypt(decryptionKeyBytes, firstOptionCodeCipherBytes);
        byte[] secondDecryptedBytes = AesCtrCrypto.decrypt(decryptionKeyBytes, secondOptionCodeCipherBytes);

        String firstDecryptedString = new String(firstDecryptedBytes);
        String secondDecryptedString = new String(secondDecryptedBytes);
        Integer firstDecryptedOption = Integer.parseInt(firstDecryptedString);
        Integer secondDecryptedOption = Integer.parseInt(secondDecryptedString);

        assertThat(firstDecryptedOption, equalTo(anOptionCode));
        assertThat(secondDecryptedOption, equalTo(anOptionCode));
    }

    @Test
    public void testEncryptedUntilHasNotExpiredYet() {
        // Given
        String votingId = voteCreationUtils.createValidVotingEncryptedUntil(Instant.now().plusSeconds(9));

        // When
        Result votingResult = votingTestClient.single(votingId);

        // Then
        JsonNode votingResultJson = GenericDataFromResult.jsonOf(votingResult);
        assertTrue(votingResultJson.get("decryptionKey").isNull());
    }
}
