package org.motechproject.commcare.provider.sync.service;

import org.motechproject.commcare.provider.sync.constants.EventConstants;
import org.motechproject.commcare.provider.sync.constants.PropertyConstants;
import org.motechproject.commcare.provider.sync.response.*;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CommCareSyncService {
    private EventRelay eventRelay;
    private CommCareHttpClientService commCareHttpClientService;
    private SettingsFacade providerSyncSettings;

    private static final Logger logger = LoggerFactory.getLogger("commcare-provider-sync");

    @Autowired
    public CommCareSyncService(EventRelay eventRelay, CommCareHttpClientService commCareHttpClientService, @Qualifier("providerSyncSettings") SettingsFacade providerSyncSettings) {
        this.eventRelay = eventRelay;
        this.commCareHttpClientService = commCareHttpClientService;
        this.providerSyncSettings = providerSyncSettings;
    }

    public void startSync(BatchJobType batchJobType) {
        fetchDetailsInBatch(new BatchRequestQuery(0), batchJobType);
    }

    public void fetchDetailsInBatch(BatchRequestQuery batchRequestQuery, BatchJobType jobType) {
        int batchSize = Integer.parseInt(providerSyncSettings.getProperty(jobType.qualify(PropertyConstants.COMMCARE_BATCH_SIZE)));

        batchRequestQuery.setBatchSize(batchSize);
        logger.info(String.format("Fetching %s in batch with %s", jobType, batchRequestQuery));
        String listUrl = providerSyncSettings.getProperty(jobType.qualify(PropertyConstants.COMMCARE_LIST_URL));
        BatchResponse batchResponse = (BatchResponse) commCareHttpClientService.fetchBatch(listUrl, batchRequestQuery, jobType.commcareResponseType());

        raiseDetailsEvent(batchResponse, jobType);

        raiseNextBatchRequestEvent(jobType, batchResponse, batchSize);
    }

    private void raiseNextBatchRequestEvent(BatchJobType jobType, BatchResponse batchResponse, int batchSize) {
        BatchResponseMetadata batchResponseMetadata = batchResponse.getMeta();
        if (!batchResponseMetadata.hasNext()) {
            logger.info(String.format("Completed %s sync event", jobType.name()));
            return;
        }

        String eventSubject = jobType.qualify(EventConstants.FETCH_DETAILS_IN_BATCH_EVENT);
        BatchRequestQuery nextBatchQuery = batchResponseMetadata.getNextBatchQuery(batchSize);
        logger.info(String.format("Raising event %s for next batch offset %s", eventSubject, nextBatchQuery.getOffset()));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EventConstants.BATCH_QUERY, nextBatchQuery);
        eventRelay.sendEventMessage(new MotechEvent(eventSubject, parameters));
    }

    private void raiseDetailsEvent(BatchResponse batchResponse, BatchJobType jobType) {
        if (!batchResponse.hasRecords()) {
            logger.info("No record found in response.");
            return;
        }

        String detailsEvent = jobType.qualify(EventConstants.DETAILS_EVENT);
        List<Provider> providers = batchResponse.getRecords();
        logger.info(String.format("Found %s records in response.", providers.size()));
        logger.info(String.format("Response meta: %s", batchResponse.getMeta()));
        logger.info(String.format("Publishing details event %s", detailsEvent));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EventConstants.DETAILS_LIST, providers);
        eventRelay.sendEventMessage(new MotechEvent(detailsEvent, parameters));
    }
}
