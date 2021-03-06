package services;

import data.entities.JpaVoting;
import data.operations.PageOfVotingsDbOperations;
import exceptions.ForbiddenException;
import play.Logger;
import requests.PageVotingsRequest;
import responses.Page;
import responses.PageVotingResponse;
import security.VerifiedJwt;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.runAsync;

public class VotingsPagingService {
    private static final Logger.ALogger logger = Logger.of(VotingsPagingService.class);

    private final PageOfVotingsDbOperations pageOfVotingsDbOperations;

    @Inject
    public VotingsPagingService(PageOfVotingsDbOperations pageOfVotingsDbOperations) {
        this.pageOfVotingsDbOperations = pageOfVotingsDbOperations;
    }

    public CompletionStage<Page<PageVotingResponse>> publicVotings(PageVotingsRequest request) {
        logger.info("publicVotings(): request = {}", request);

        return pageOfVotingsDbOperations.pageOfPublic(getOffsetOrDefault(request), getLimitOrDefault(request))
                .thenApply(this::toPageOfPageVotingResponse);
    }

    public CompletionStage<Page<PageVotingResponse>> votingsOfVoteCaller(PageVotingsRequest request, VerifiedJwt jwt) {
        logger.info("votingsOfVoteCaller(): userId = {}, request = {}", jwt.getUserId(), request);

        int offset = getOffsetOrDefault(request);
        int limit = getLimitOrDefault(request);

        return checkIfUserIsAllowedToPageVotingsOfVoteCallers(jwt)
                .thenCompose(v -> pageOfVotingsDbOperations.votingsOfVoteCaller(offset, limit, jwt.getUserId()))
                .thenApply(this::toPageOfPageVotingResponse);
    }

    public CompletionStage<Page<PageVotingResponse>> votingsOfVoter(PageVotingsRequest request, VerifiedJwt jwt) {
        logger.info("votingsOfVoter(): userId = {}, request = {}", jwt.getUserId(), request);

        int offset = getOffsetOrDefault(request);
        int limit = getLimitOrDefault(request);

        return checkIfUserIsAllowedToPageVotingsOfVoters(jwt)
                .thenCompose(v -> pageOfVotingsDbOperations.votingsOfVoter(offset, limit, jwt.getUserId()))
                .thenApply(this::toPageOfPageVotingResponse);
    }

    private int getOffsetOrDefault(PageVotingsRequest request) {
        if (request.getOffset() == null) {
            return 0;
        }

        return request.getOffset();
    }

    private int getLimitOrDefault(PageVotingsRequest request) {
        if (request.getLimit() == null) {
            return 25;
        }

        return request.getLimit();
    }

    private Page<PageVotingResponse> toPageOfPageVotingResponse(Page<JpaVoting> jpaVotingPage) {
        List<PageVotingResponse> pageVotingResponses = jpaVotingPage.getItems().stream()
                .map(this::toPageVotingResponse)
                .collect(Collectors.toList());

        Page<PageVotingResponse> result = new Page<>();
        result.setItems(pageVotingResponses);
        result.setTotalCount(jpaVotingPage.getTotalCount());

        return result;
    }

    private PageVotingResponse toPageVotingResponse(JpaVoting jpaVoting) {
        PageVotingResponse pageVotingResponse = new PageVotingResponse();
        pageVotingResponse.setTitle(jpaVoting.getTitle());
        pageVotingResponse.setId(Base62Conversions.encode(jpaVoting.getId()));

        return pageVotingResponse;
    }

    private CompletionStage<Void> checkIfUserIsAllowedToPageVotingsOfVoteCallers(VerifiedJwt jwt) {
        return runAsync(() -> {
            if (!jwt.hasVoteCallerRole()) {
                String message = String.format("User %s is not allowed to page vote-caller votings!", jwt.getUserId());
                logger.warn("checkIfUserIsAllowedToPageVotingsOfVoteCallers(): {}", message);
                throw new ForbiddenException(message);
            }
        });
    }

    private CompletionStage<Void> checkIfUserIsAllowedToPageVotingsOfVoters(VerifiedJwt jwt) {
        return runAsync(() -> {
            if (!jwt.hasVoterRole()) {
                String message = String.format("User %s is not allowed to page voter votings!", jwt.getUserId());
                logger.warn("checkIfUserIsAllowedToPageVotingsOfVoters(): {}", message);
                throw new ForbiddenException(message);
            }
        });
    }
}
