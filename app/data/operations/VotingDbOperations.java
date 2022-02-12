package data.operations;

import data.entities.JpaVoting;
import data.repositories.ChannelProgressRepository;
import data.repositories.VotingRepository;
import devote.blockchain.api.ChannelGenerator;
import devote.blockchain.api.DistributionAndBallotAccountOperation;
import executioncontexts.DatabaseExecutionContext;
import play.Logger;
import requests.CreateVotingRequest;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;

public class VotingDbOperations {
    private final DatabaseExecutionContext dbExecContext;
    private final VotingRepository votingRepository;
    private final ChannelProgressRepository channelProgressRepository;

    private static final Logger.ALogger logger = Logger.of(VotingDbOperations.class);

    @Inject
    public VotingDbOperations(
            DatabaseExecutionContext dbExecContext,
            VotingRepository votingRepository,
            ChannelProgressRepository channelProgressRepository) {
        this.dbExecContext = dbExecContext;
        this.votingRepository = votingRepository;
        this.channelProgressRepository = channelProgressRepository;
    }

    public CompletionStage<Long> initialize(CreateVotingRequest createVotingRequest) {
        return supplyAsync(() -> {
            logger.info("initialize(): createVotingRequest = {}", createVotingRequest);
            return votingRepository.initialize(createVotingRequest);
        }, dbExecContext);
    }

    public CompletionStage<JpaVoting> single(Long id) {
        return supplyAsync(() -> {
            logger.info("single(): id = {}", id);
            return votingRepository.single(id);
        }, dbExecContext);
    }

    public CompletionStage<Void> channelGeneratorsCreated(Long votingId, List<ChannelGenerator> channelGenerators) {
        return runAsync(() -> {
            logger.info("channelGeneratorsCreated(): votingId = {}, accounts size = {}", votingId, channelGenerators.size());
            votingRepository.channelGeneratorsCreated(votingId, channelGenerators);
            channelProgressRepository.issuersCreated(votingId);
        }, dbExecContext);
    }

    public CompletionStage<Void> distributionAndBallotAccountsCreated(Long votingId, DistributionAndBallotAccountOperation.TransactionResult txResult, String assetCode) {
        return runAsync(() -> {
            logger.info("distributionAndBallotAccountsCreated(): votingId = {}", votingId);
            votingRepository.distributionAndBallotAccountsCreated(votingId, txResult, assetCode);
        }, dbExecContext);
    }

    public CompletionStage<Void> votingSavedToIpfs(Long votingId, String cid) {
        return runAsync(() -> {
            logger.info("votingSavedToIpfs(): votingId = {}, cid = {}", votingId, cid);
            votingRepository.votingSavedToIpfs(votingId, cid);
        }, dbExecContext);

    }
}
