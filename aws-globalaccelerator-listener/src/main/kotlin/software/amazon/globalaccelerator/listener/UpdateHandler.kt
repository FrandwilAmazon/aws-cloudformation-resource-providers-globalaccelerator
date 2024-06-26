package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.PortRange
import com.amazonaws.services.globalaccelerator.model.UpdateListenerRequest
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode

/**
 * Update handler implementation for listener resource.
 */
class UpdateHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Update Listener Request $request")
        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES, pendingStabilization = false)
        val model = request.desiredResourceState
        return if (!inferredCallbackContext.pendingStabilization) {
            HandlerCommons.getListener(model.listenerArn, proxy, agaClient, logger)
                    ?: return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotFound, "Listener Not Found")
            updateListenerStep(model, proxy, agaClient)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun updateListenerStep(model: ResourceModel,
                                   proxy: AmazonWebServicesClientProxy,
                                   agaClient: AWSGlobalAccelerator): ProgressEvent<ResourceModel, CallbackContext?> {
        val listener = updateListener(model, proxy, agaClient)
        model.clientAffinity = listener.clientAffinity
        model.protocol = listener.protocol
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = (HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES),
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }

    @SuppressFBWarnings(value = ["BC_BAD_CAST_TO_ABSTRACT_COLLECTION"], justification = "Generated bytecode for Iterable.map casts to an abstract collection")
    private fun updateListener(model: ResourceModel,
                               proxy: AmazonWebServicesClientProxy,
                               agaClient: AWSGlobalAccelerator): Listener {
        val convertedPortRanges = model.portRanges.map { x ->
            PortRange().withFromPort(x.fromPort).withToPort(x.toPort)
        }
        val updateListenerRequest = UpdateListenerRequest()
                .withListenerArn(model.listenerArn)
                .withClientAffinity(model.clientAffinity)
                .withProtocol(model.protocol)
                .withPortRanges(convertedPortRanges)
        return proxy.injectCredentialsAndInvoke(updateListenerRequest, agaClient::updateListener).listener
    }
}
