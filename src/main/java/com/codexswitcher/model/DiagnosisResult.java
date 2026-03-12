package com.codexswitcher.model;

import java.util.ArrayList;
import java.util.List;

public class DiagnosisResult {

    private String conclusion;
    private String detail;
    private String summaryDetail;
    private String successEndpoint;
    private Boolean modelSupported;
    private Boolean modelInList;
    private String modelSource;
    private String responseModel;
    private String responseModelSource;
    private String baseHost;
    private final List<String> supportedLabels = new ArrayList<>();
    private final List<String> supportedUrls = new ArrayList<>();
    private final List<ProbeResult> results = new ArrayList<>();

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getSummaryDetail() {
        return summaryDetail;
    }

    public void setSummaryDetail(String summaryDetail) {
        this.summaryDetail = summaryDetail;
    }

    public String getSuccessEndpoint() {
        return successEndpoint;
    }

    public void setSuccessEndpoint(String successEndpoint) {
        this.successEndpoint = successEndpoint;
    }

    public Boolean getModelSupported() {
        return modelSupported;
    }

    public void setModelSupported(Boolean modelSupported) {
        this.modelSupported = modelSupported;
    }

    public Boolean getModelInList() {
        return modelInList;
    }

    public void setModelInList(Boolean modelInList) {
        this.modelInList = modelInList;
    }

    public String getModelSource() {
        return modelSource;
    }

    public void setModelSource(String modelSource) {
        this.modelSource = modelSource;
    }

    public String getResponseModel() {
        return responseModel;
    }

    public void setResponseModel(String responseModel) {
        this.responseModel = responseModel;
    }

    public String getResponseModelSource() {
        return responseModelSource;
    }

    public void setResponseModelSource(String responseModelSource) {
        this.responseModelSource = responseModelSource;
    }

    public String getBaseHost() {
        return baseHost;
    }

    public void setBaseHost(String baseHost) {
        this.baseHost = baseHost;
    }

    public List<String> getSupportedLabels() {
        return supportedLabels;
    }

    public List<String> getSupportedUrls() {
        return supportedUrls;
    }

    public List<ProbeResult> getResults() {
        return results;
    }
}
