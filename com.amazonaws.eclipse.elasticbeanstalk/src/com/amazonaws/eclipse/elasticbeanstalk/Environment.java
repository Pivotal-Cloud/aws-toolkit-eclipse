/*
 * Copyright 2010-2011 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.eclipse.elasticbeanstalk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jst.server.core.IWebModule;
import org.eclipse.jst.server.core.internal.J2EEUtil;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IModuleType;
import org.eclipse.wst.server.core.internal.ServerWorkingCopy;
import org.eclipse.wst.server.core.internal.facets.FacetUtil;
import org.eclipse.wst.server.core.model.ServerDelegate;

import com.amazonaws.eclipse.core.AwsToolkitCore;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.ConfigurationOptionSetting;
import com.amazonaws.services.elasticbeanstalk.model.ConfigurationSettingsDescription;
import com.amazonaws.services.elasticbeanstalk.model.DescribeConfigurationSettingsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentResourcesRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentResourcesResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.Instance;

@SuppressWarnings("restriction")
public class Environment extends ServerDelegate {

    private static final String PROPERTY_REGION_ENDPOINT         = "regionEndpoint";
    private static final String PROPERTY_APPLICATION_NAME        = "applicationName";
    private static final String PROPERTY_APPLICATION_DESCRIPTION = "applicationDescription";
    private static final String PROPERTY_ENVIRONMENT_NAME        = "environmentName";
    private static final String PROPERTY_ENVIRONMENT_DESCRIPTION = "environmentDescription";
    private static final String PROPERTY_KEY_PAIR_NAME           = "keyPairName";
    private static final String PROPERTY_CNAME                   = "cname";
    private static final String PROPERTY_HEALTHCHECK_URL         = "healthcheckUrl";
    private static final String PROPERTY_SSL_CERT_ID             = "sslCertId";
    private static final String PROPERTY_ACCOUNT_ID              = "accountId";
    private static final String PROPERTY_SNS_ENDPOINT            = "snsEndpoint";
    private static final String PROPERTY_SOLUTION_STACK          = "solutionStack";

    private static Map<String, EnvironmentDescription> map = new HashMap<String, EnvironmentDescription>();

    @Override
    public void setDefaults(IProgressMonitor monitor) {
        // Disable auto publishing
        setAttribute("auto-publish-setting", 1);
    }

    public String getAccountId() {
        return getAttribute(PROPERTY_ACCOUNT_ID, (String)null);
    }

    public void setAccountId(String accountId) {
        setAttribute(PROPERTY_ACCOUNT_ID, accountId);
    }

    public String getRegionEndpoint() {
       return getAttribute(PROPERTY_REGION_ENDPOINT, (String)null);
    }

    public void setRegionEndpoint(String regionEndpoint) {
        setAttribute(PROPERTY_REGION_ENDPOINT, regionEndpoint);
    }

    public String getApplicationName() {
        return getAttribute(PROPERTY_APPLICATION_NAME, (String)null);
    }

    public void setApplicationName(String applicationName) {
        setAttribute(PROPERTY_APPLICATION_NAME, applicationName);
    }

    public String getApplicationDescription() {
        return getAttribute(PROPERTY_APPLICATION_NAME, (String)null);
    }

    public void setApplicationDescription(String applicationDescription) {
        setAttribute(PROPERTY_APPLICATION_DESCRIPTION, applicationDescription);
    }

    public String getEnvironmentName() {
        return getAttribute(PROPERTY_ENVIRONMENT_NAME, (String)null);
    }

    public void setEnvironmentName(String environmentName) {
        setAttribute(PROPERTY_ENVIRONMENT_NAME, environmentName);
    }

    public String getEnvironmentDescription() {
        return getAttribute(PROPERTY_ENVIRONMENT_DESCRIPTION, (String)null);
    }

    public void setEnvironmentDescription(String environmentDescription) {
        setAttribute(PROPERTY_ENVIRONMENT_DESCRIPTION, environmentDescription);
    }

    public String getEnvironmentUrl() {
        EnvironmentDescription cachedEnvironmentDescription = getCachedEnvironmentDescription();
        if (cachedEnvironmentDescription == null) return null;
        if (cachedEnvironmentDescription.getCNAME() == null) return null;
        return "http://" + cachedEnvironmentDescription.getCNAME();
    }

    public String getCname() {
        return getAttribute(PROPERTY_CNAME, (String)null);
    }

    public void setCname(String cname) {
        setAttribute(PROPERTY_CNAME, (String)cname);
    }

    public String getKeyPairName() {
        return getAttribute(PROPERTY_KEY_PAIR_NAME, (String) null);
    }

    public void setKeyPairName(String keyPairName) {
        setAttribute(PROPERTY_KEY_PAIR_NAME, keyPairName);
    }

    public String getSslCertificateId() {
        return getAttribute(PROPERTY_SSL_CERT_ID, (String) null);
    }

    public void setSslCertificateId(String sslCertificateId) {
        setAttribute(PROPERTY_SSL_CERT_ID, sslCertificateId);
    }

    public String getHealthCheckUrl() {
        return getAttribute(PROPERTY_HEALTHCHECK_URL, (String)null);
    }

    public void setHealthCheckUrl(String healthCheckUrl) {
        setAttribute(PROPERTY_HEALTHCHECK_URL, healthCheckUrl);
    }

    public String getSnsEndpoint() {
        return getAttribute(PROPERTY_SNS_ENDPOINT, (String)null);
    }

    public void setSnsEndpoint(String snsEndpoint) {
        setAttribute(PROPERTY_SNS_ENDPOINT, snsEndpoint);
    }

    public String getSolutionStack() {
        return getAttribute(PROPERTY_SOLUTION_STACK, (String)null);
    }

    public void setSolutionStack(String solutionStackForServerType) {
        setAttribute(PROPERTY_SOLUTION_STACK, solutionStackForServerType);
    }


    /*
     * TODO: We can't quite turn this on yet because WTPWarUtils runs an operation that tries to lock
     *       the whole workspace when it exports the WAR for a project.  If we can figure out how to
     *       get that to not lock the whole workspace, then we can turn this back on.
     */
//    public boolean isUseProjectSpecificSchedulingRuleOnPublish() {
//        return true;
//    }

    /* (non-Javadoc)
     * @see org.eclipse.wst.server.core.model.ServerDelegate#canModifyModules(org.eclipse.wst.server.core.IModule[], org.eclipse.wst.server.core.IModule[])
     */
    @Override
    public IStatus canModifyModules(IModule[] add, IModule[] remove) {
        // If we're not adding any modules, we know this request is fine
        if (add == null) return Status.OK_STATUS;

        if (add.length > 1) return new Status(Status.ERROR, ElasticBeanstalkPlugin.PLUGIN_ID,
            "Only one web application can run in each AWS Elastic Beanstalk environment");

        for (IModule module : add) {
            String moduleTypeId = module.getModuleType().getId().toLowerCase();
            if (moduleTypeId.equals("jst.web") == false) {
                return new Status(IStatus.ERROR, ElasticBeanstalkPlugin.PLUGIN_ID,
                    "Unsupported module type: " + module.getModuleType().getName());
            }

            if (module.getProject() != null) {
                IStatus status = FacetUtil.verifyFacets(module.getProject(), getServer());
                if (status != null && !status.isOK()) return status;
            }
        }

        return Status.OK_STATUS;
    }

    /* (non-Javadoc)
     * @see org.eclipse.wst.server.core.model.ServerDelegate#getChildModules(org.eclipse.wst.server.core.IModule[])
     */
    @Override
    public IModule[] getChildModules(IModule[] module) {
        if (module == null) return null;

        IModuleType moduleType = module[0].getModuleType();

        if (module.length == 1 && moduleType != null && "jst.web".equalsIgnoreCase(moduleType.getId())) {
            IWebModule webModule = (IWebModule)module[0].loadAdapter(IWebModule.class, null);
            if (webModule != null) {
                return webModule.getModules();
            }
        }

        return new IModule[0];
    }

    /* (non-Javadoc)
     * @see org.eclipse.wst.server.core.model.ServerDelegate#getRootModules(org.eclipse.wst.server.core.IModule)
     */
    @Override
    public IModule[] getRootModules(IModule module) throws CoreException {
        String moduleTypeId = module.getModuleType().getId().toLowerCase();
        if (moduleTypeId.equals("jst.web")) {
            IStatus status = canModifyModules(new IModule[] {module}, null);
            if (status == null || !status.isOK()) {
                throw new CoreException(status);
            }

            return new IModule[] {module};
        }

        return J2EEUtil.getWebModules(module, null);
    }

    /* (non-Javadoc)
     * @see org.eclipse.wst.server.core.model.ServerDelegate#modifyModules(org.eclipse.wst.server.core.IModule[], org.eclipse.wst.server.core.IModule[], org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public void modifyModules(IModule[] add, IModule[] remove, IProgressMonitor monitor) throws CoreException {
        IStatus status = canModifyModules(add, remove);
        if (status == null || !status.isOK()) {
            throw new CoreException(status);
        }

        if (add != null && add.length > 0 && getServer().getModules().length > 0) {
            ServerWorkingCopy serverWorkingCopy = (ServerWorkingCopy)getServer();
            serverWorkingCopy.modifyModules(new IModule[0], serverWorkingCopy.getModules(), monitor);
        }
    }

    public void setCachedEnvironmentDescription(EnvironmentDescription environmentDescription) {
        map.put(getServer().getId(), environmentDescription);
    }

    public EnvironmentDescription getCachedEnvironmentDescription() {
        return map.get(getServer().getId());
    }

    /*
     * Utility methods for communicating with environments
     */

    /**
     * Returns the environment's configured remote debugging port, or null if it
     * cannot be determined.
     */
    public static String getDebugPort(List<ConfigurationSettingsDescription> settings) {
        ConfigurationOptionSetting opt = Environment.getJVMOptions(settings);
        if ( opt != null ) {
            return getDebugPort(opt.getValue());
        }
        return null;
    }

    /**
     * Returns the debug port in the JVM options string given, or null if it isn't present.
     */
    public static String getDebugPort(String jvmOptions) {
        if ( jvmOptions.contains("-Xdebug") && jvmOptions.contains("-Xrunjdwp:") ) {
            Matcher matcher = Pattern.compile("-Xrunjdwp:\\S*address=(\\d+)").matcher(jvmOptions);
            if ( matcher.find() && matcher.groupCount() > 0 && matcher.group(1) != null ) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Returns the "JVM Options" configuration setting, if it exists, or null otherwise.
     */
    public static ConfigurationOptionSetting getJVMOptions(List<ConfigurationSettingsDescription> settings) {
        for (ConfigurationSettingsDescription setting : settings) {
            for (ConfigurationOptionSetting opt : setting.getOptionSettings()) {
                if (opt.getOptionName().equals("JVM Options") && opt.getNamespace().equals(ConfigurationOptionConstants.JVMOPTIONS)) {
                    return opt;
                }
            }
        }
        return null;
    }

    /**
     * Returns the security group name given in the configuration settings, or null if it cannot be determined.
     */
    public static String getSecurityGroup(List<ConfigurationSettingsDescription> settings) {
        for (ConfigurationSettingsDescription setting : settings) {
            for (ConfigurationOptionSetting opt : setting.getOptionSettings()) {
                if (opt.getOptionName().equals("SecurityGroups") && opt.getNamespace().equals(ConfigurationOptionConstants.LAUNCHCONFIGURATION)) {
                    return opt.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Returns whether the given port is open on the security group for the
     * environment settings given.
     */
    public boolean isIngressAllowed(int port, List<ConfigurationSettingsDescription> settings) {
        String securityGroup = Environment.getSecurityGroup(settings);

        if (securityGroup == null)
            throw new RuntimeException("Couldn't determine security group of environent");

        AmazonEC2 ec2 = getEc2Client();

        DescribeSecurityGroupsResult describeSecurityGroups = ec2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withGroupNames(securityGroup));
        for (SecurityGroup group : describeSecurityGroups.getSecurityGroups()) {
            if (ingressAllowed(group, port)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @see Environment#isIngressAllowed(int, List)
     */
    public boolean isIngressAllowed(String port, List<ConfigurationSettingsDescription> settings) {
        return isIngressAllowed(Integer.parseInt(port), settings);
    }

    /**
     * Returns an EC2 client configured to talk to the appropriate region for
     * this environment.
     */
    public AmazonEC2 getEc2Client() {
        return AwsToolkitCore.getClientFactory(getAccountId()).getEC2ClientByEndpoint(
                "ec2.us-east-1.amazonaws.com");
    }

    /**
     * Returns whether the group given allows TCP ingress on the port given.
     */
    private boolean ingressAllowed(SecurityGroup group, int debugPortInt) {
        for (IpPermission permission : group.getIpPermissions()) {
            if ("tcp".equals(permission.getIpProtocol()) && permission.getIpRanges() != null && permission.getIpRanges().contains("0.0.0.0/0")) {
                if (permission.getFromPort() != null && permission.getFromPort() <= debugPortInt
                    && permission.getToPort() != null && permission.getToPort() >= debugPortInt) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the Beanstalk environment represented by this WTP server
     * has been created yet.
     */
    public boolean doesEnvironmentExistInBeanstalk() {
        AWSElasticBeanstalk beanstalk = getClient();

        DescribeEnvironmentsResult describeEnvironmentsResult = beanstalk.describeEnvironments(
            new DescribeEnvironmentsRequest().withEnvironmentNames(getEnvironmentName()).withApplicationName(getApplicationName()));

        return (describeEnvironmentsResult.getEnvironments().size() > 0);
    }

    /**
     * Returns the list of current settings for this environment
     */
    public List<ConfigurationSettingsDescription> getCurrentSettings() {
        if (doesEnvironmentExistInBeanstalk() == false) return new ArrayList<ConfigurationSettingsDescription>();

        AWSElasticBeanstalk beanstalk = getClient();
        return beanstalk.describeConfigurationSettings(
            new DescribeConfigurationSettingsRequest().withEnvironmentName(getEnvironmentName())
            .withApplicationName(getApplicationName())).getConfigurationSettings();
    }

    /**
     * Returns a client for this environment.
     */
    public AWSElasticBeanstalk getClient() {
        return AwsToolkitCore.getClientFactory(getAccountId())
                .getElasticBeanstalkClientByEndpoint(getRegionEndpoint());
    }

    /**
     * Opens up the port given on the security group for the environment
     * settings given.
     */
    public void openSecurityGroupPort(int debugPort, String securityGroup) {
        getEc2Client().authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest().withCidrIp("0.0.0.0/0")
                .withFromPort(debugPort).withToPort(debugPort).withIpProtocol("tcp")
                .withGroupName(securityGroup));
    }

    /**
     * @see Environment#openSecurityGroupPort(int, String)
     */
    public void openSecurityGroupPort(String debugPort, String securityGroup) {
        openSecurityGroupPort(Integer.parseInt(debugPort), securityGroup);
    }

    /**
     * Returns the EC2 instance IDs being used by this environment.
     */
    public List<String> getEC2InstanceIds() {
        AWSElasticBeanstalk client = AwsToolkitCore.getClientFactory(getAccountId())
                .getElasticBeanstalkClientByEndpoint(getRegionEndpoint());
        DescribeEnvironmentResourcesResult describeEnvironmentResources = client
                .describeEnvironmentResources(new DescribeEnvironmentResourcesRequest()
                        .withEnvironmentName(getEnvironmentName()));
        List<String> instanceIds = new ArrayList<String>(describeEnvironmentResources.getEnvironmentResources()
                .getInstances().size());
        for ( Instance i : describeEnvironmentResources.getEnvironmentResources().getInstances() ) {
            instanceIds.add(i.getId());
        }
        return instanceIds;
    }

}
