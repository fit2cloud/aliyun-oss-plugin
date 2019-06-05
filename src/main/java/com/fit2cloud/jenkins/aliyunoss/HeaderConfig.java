package com.fit2cloud.jenkins.aliyunoss;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author lihao
 */
public class HeaderConfig extends AbstractDescribableImpl<HeaderConfig> {
    private String postfix;
    private String name;
    private String value;

    @DataBoundConstructor
    public HeaderConfig(String postfix, String name, String value) {
        this.postfix = postfix;
        this.name = name;
        this.value = value;
    }

    public String getPostfix() {
        return postfix;
    }

    public void setPostfix(String postfix) {
        this.postfix = postfix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<HeaderConfig> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
