package org.motechproject.commcare.provider.sync.web.controller;

import org.apache.commons.io.output.NullWriter;
import org.motechproject.commcare.provider.sync.constants.EventConstants;
import org.motechproject.commcare.provider.sync.diagnostics.AllDiagnosticsProbes;
import org.motechproject.commcare.provider.sync.diagnostics.DiagnosticsStatus;
import org.motechproject.commcare.provider.sync.diagnostics.scheduler.DiagnosticsContext;
import org.motechproject.commcare.provider.sync.diagnostics.scheduler.SchedulerDiagnosticsProbe;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping(value = "/web-api/commcare-provider-sync/diagnostics")
public class DiagnosticsController {

    private AllDiagnosticsProbes allDiagnosticsProbes;

    @Autowired
    public DiagnosticsController(AllDiagnosticsProbes allDiagnosticsProbes) {
        this.allDiagnosticsProbes = allDiagnosticsProbes;
    }

    @RequestMapping(value = "/complete", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> complete() throws SchedulerException {
        StringWriter stringWriter = new StringWriter();
        allDiagnosticsProbes.diagnose(getDiagnosticsContext(), stringWriter);

        return createResponseEntity(stringWriter.toString());
    }

    @RequestMapping(value = "/result", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> result() throws SchedulerException {
        DiagnosticsStatus diagnosticsStatus = allDiagnosticsProbes.diagnose(getDiagnosticsContext(), NullWriter.NULL_WRITER);
        return createResponseEntity(diagnosticsStatus == DiagnosticsStatus.PASS ? "SUCCESS" : "FAILED");
    }

    private ResponseEntity<String> createResponseEntity(String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private DiagnosticsContext getDiagnosticsContext() {
        DiagnosticsContext diagnosticsContext = new DiagnosticsContext();
        diagnosticsContext.set(SchedulerDiagnosticsProbe.SCHEDULE_LIST_TO_PROBE, getSchedulesToDiagnose());
        return diagnosticsContext;
    }

    private List<String> getSchedulesToDiagnose() {
        List<String> schedules = new ArrayList<>();
        schedules.add(EventConstants.GROUP_SYNC_EVENT + "-" + EventConstants.GROUP_SYNC_JOB_ID_KEY);
        schedules.add(EventConstants.PROVIDER_SYNC_EVENT + "-" + EventConstants.PROVIDER_SYNC_JOB_ID_KEY);
        return schedules;
    }
}