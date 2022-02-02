package data.repositories.imp;

import data.entities.JpaChannelAccountProgress;
import data.entities.JpaVoting;
import data.entities.JpaVotingIssuerAccount;
import data.repositories.ChannelProgressRepository;
import io.ebean.EbeanServer;
import play.Logger;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class EbeanChannelProgressRepository implements ChannelProgressRepository {
    private static final Logger.ALogger logger = Logger.of(EbeanChannelProgressRepository.class);

    private final EbeanServer ebeanServer;

    @Inject
    public EbeanChannelProgressRepository(EbeanServer ebeanServer) {
        this.ebeanServer = ebeanServer;
    }

    @Override
    public void channelAccountsCreated(Long id, int numOfAccountsCreated) {
        logger.info("channelAccountsCreated(): id = {}, numOfAccountsCreated = {}", id, numOfAccountsCreated);

        JpaChannelAccountProgress channelProgress = ebeanServer.find(JpaChannelAccountProgress.class, id);

        channelProgress.setNumOfAccountsLeftToCreate(
                channelProgress.getNumOfAccountsLeftToCreate() - numOfAccountsCreated
        );

        if (channelProgress.getNumOfAccountsLeftToCreate() == 0) {
            logger.info("channelAccountsCreated(): channel progress with id = {} finished!", id);
        }

        ebeanServer.update(channelProgress);
    }

    @Override
    public List<JpaChannelAccountProgress> notFinishedSampleOf(int sampleSize) {
        logger.info("notFinishedSampleOf(): sampleSize = {}", sampleSize);

        return ebeanServer.createQuery(JpaChannelAccountProgress.class)
                .where()
                .gt("numOfAccountsLeftToCreate", 0)
                .setMaxRows(sampleSize)
                .findList();
    }

    @Override
    public void issuersCreated(Long votingId) {
        JpaVoting voting = ebeanServer.find(JpaVoting.class, votingId);
        List<JpaVotingIssuerAccount> issuers = voting.getIssuerAccounts();

        logger.info("issuersCreated(): Total channel accounts to create: {}", voting.getVotesCap());
        logger.info("issuersCreated(): Creating {} channel progresses for voting with id = {}.",
                issuers.size(), votingId);
        List<Long> votesCapOfIssuers = issuers.stream()
                .map(JpaVotingIssuerAccount::getVotesCap)
                .collect(Collectors.toList());
        logger.info("issuersCreated(): Number of channel accounts to create for issuers: {}", votesCapOfIssuers);

        List<JpaChannelAccountProgress> progresses = issuers.stream()
                .map(this::fromIssuer)
                .collect(Collectors.toList());

        progresses.forEach(ebeanServer::save);
    }

    private JpaChannelAccountProgress fromIssuer(JpaVotingIssuerAccount issuer) {
        JpaChannelAccountProgress progress = new JpaChannelAccountProgress();
        progress.setIssuer(issuer);
        progress.setNumOfAccountsToCreate(issuer.getVotesCap());
        progress.setNumOfAccountsLeftToCreate(issuer.getVotesCap());
        return progress;
    }
}
