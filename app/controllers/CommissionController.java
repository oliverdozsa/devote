package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import play.Logger;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import requests.CommissionInitRequest;
import responses.CommissionInitResponse;
import security.SecurityUtils;
import security.VerifiedJwt;
import services.CommissionService;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class CommissionController extends Controller {
    private final FormFactory formFactory;
    private final CommissionService commissionService;

    private static final Logger.ALogger logger = Logger.of(CommissionController.class);

    private final Function<Throwable, Result> mapException = new DefaultExceptionMapper(logger);
    private final Function<Throwable, Result> mapExceptionWithUnpack = e -> mapException.apply(e.getCause());

    @Inject
    public CommissionController(FormFactory formFactory, CommissionService commissionService) {
        this.formFactory = formFactory;
        this.commissionService = commissionService;
    }

    public CompletionStage<Result> init(Http.Request request) {
        logger.info("init()");

        Form<CommissionInitRequest> form = formFactory.form(CommissionInitRequest.class).bindFromRequest(request);

        if (form.hasErrors()) {
            JsonNode errorJson = form.errorsAsJson();
            logger.warn("init(): Form has errors! error json:\n{}", errorJson.toPrettyString());

            return completedFuture(badRequest(errorJson));
        } else {
            VerifiedJwt jwt = SecurityUtils.getFromRequest(request);

            return commissionService.init(form.get(), jwt)
                    .thenApply(this::toResult)
                    .exceptionally(mapExceptionWithUnpack);
        }
    }

    public CompletionStage<Result> envelop(Http.Request request) {
        // TODO: Put a message in a envelope preparing it for signing. This call
        //       should be used anonymously
        return null;
    }

    private Result toResult(CommissionInitResponse initResponse) {
        // TODO
        return null;
    }
}