/*
 * Copyright 2010-2011 Amazon Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://aws.amazon.com/apache2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amazonaws.eclipse.elasticbeanstalk.server.ui.configEditor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.databinding.observable.Observables;
import org.eclipse.core.databinding.observable.map.IObservableMap;
import org.eclipse.core.databinding.observable.map.WritableMap;
import org.eclipse.core.databinding.observable.set.WritableSet;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.swt.widgets.Display;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.eclipse.core.AwsToolkitCore;
import com.amazonaws.eclipse.elasticbeanstalk.Environment;
import com.amazonaws.eclipse.elasticbeanstalk.server.ui.CancelableThread;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.ConfigurationOptionDescription;
import com.amazonaws.services.elasticbeanstalk.model.ConfigurationOptionSetting;
import com.amazonaws.services.elasticbeanstalk.model.ConfigurationSettingsDescription;
import com.amazonaws.services.elasticbeanstalk.model.DescribeConfigurationOptionsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeConfigurationOptionsResult;

/**
 * Simple data model factory for environments.
 */
public class EnvironmentConfigDataModel {

    private static final Map<Environment, EnvironmentConfigDataModel> models = new HashMap<Environment, EnvironmentConfigDataModel>();

    private final Environment environment;
    private final IObservableMap dataModel;
    private final List<ConfigurationOptionDescription> options;

    private final Map<OptionKey, IObservableValue> sharedObservables;
    private final List<RefreshListener> listeners;
    private CancelableThread refreshThread;

    private EnvironmentConfigDataModel(Environment environment) {
        this.environment = environment;
        dataModel = new WritableMap();
        sharedObservables = new HashMap<OptionKey, IObservableValue>();
        listeners = new LinkedList<RefreshListener>();
        options = new LinkedList<ConfigurationOptionDescription>();
    }

    /**
     * Returns a data model for the environment given.
     */
    public static synchronized EnvironmentConfigDataModel getInstance(Environment environment) {
        if ( !models.containsKey(environment) ) {
            models.put(environment, new EnvironmentConfigDataModel(environment));
        }
        return models.get(environment);
    }

    /**
     * Returns the raw model, which is a typeless map. It's not generally safe
     * to observe this map further.
     *
     * @see EnvironmentConfigDataModel#observeEntry(ConfigurationOptionDescription)
     */
    public IObservableMap getDataModel() {
        return dataModel;
    }

    /**
     * Observes the entry described by the key given. This must be used, rather
     * than observing the map entries directly, to ensure proper sharing of
     * observables.
     */
    public synchronized IObservableValue observeEntry(ConfigurationOptionDescription key) {
        OptionKey optionKey = getKey(key);
        if ( !sharedObservables.containsKey(optionKey) ) {
            IObservableValue observable = Observables.observeMapEntry(dataModel, optionKey);
            sharedObservables.put(optionKey, observable);
        }
        return sharedObservables.get(optionKey);
    }

    /**
     * Returns the data model entry corresponding to the given key.
     */
    public synchronized Object getEntry(ConfigurationOptionDescription key) {
        return dataModel.get(getKey(key));
    }

    /**
     * Initializes the model given with the values provided, overwriting any
     * previous values.
     */
    public synchronized void init(final Map<String, List<ConfigurationOptionSetting>> settings,
                                  final List<ConfigurationOptionDescription> options) {
        this.options.clear();
        this.options.addAll(options);

        for ( ConfigurationOptionDescription opt : options ) {
            List<ConfigurationOptionSetting> settingsInNamespace = settings.get(opt.getNamespace());
            if ( settingsInNamespace != null ) {
                for ( ConfigurationOptionSetting setting : settingsInNamespace ) {
                    if ( opt.getName().equals(setting.getOptionName()) ) {
                        String valueType = opt.getValueType();
                        OptionKey key = getKey(opt);
                        if ( valueType.equals("Scalar") ) {
                            dataModel.put(key, setting.getValue());
                        } else if ( valueType.equals("Boolean") ) {
                            dataModel.put(key, Boolean.valueOf(setting.getValue()));
                        } else if ( valueType.equals("List") ) {
                            if ( !dataModel.containsKey(key) ) {
                                dataModel.put(key, new WritableSet());
                            }
                            synchronizeSets(setting, key);
                        }
                    }
                }
            }
        }
    }

    /**
     * Register a listener for refresh events.
     */
    public synchronized void addRefreshListener(RefreshListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes the given listener.
     */
    public synchronized void removeRefreshListener(RefreshListener listener) {
        listeners.remove(listener);
    }

    /**
     * Asynchronously refreshes the model with services calls. To be notified of
     * refresh lifecycle events, register a listener.
     *
     * @param templateName
     *            If non-null, will initialize using the values in the template
     *            named, rather than the environment's running settings.
     */
    public synchronized void refresh(String templateName) {
        cancelThread(refreshThread);
        refreshThread = new RefreshThread(templateName);
        refreshThread.start();
    }

    public List<ConfigurationOptionDescription> getOptions() {
        return options;
    }

    /**
     * Synchronizes the two sets given in such as a way as to not provoke a set
     * changed event if their contents are equal.
     */
    void synchronizeSets(ConfigurationOptionSetting setting, OptionKey key) {
        Set<String> settingValues = new HashSet<String>();

        @SuppressWarnings("unchecked")
        Collection<String> modelValues = ((Collection<String>) dataModel.get(key));

        /*
         * Sets a and b are equivalent iff for each element e in set a, b.contains(e) and
         * vice versa
         */
        boolean setsEquivalent = true;
        for ( String v : setting.getValue().split(",") ) {
            settingValues.add(v);
            if ( !modelValues.contains(v) ) {
                setsEquivalent = false;
            }
        }
        for (String v : modelValues) {
            if (!settingValues.contains(v))
                setsEquivalent = false;
        }

        if ( !setsEquivalent ) {
            modelValues.clear();
            modelValues.addAll(settingValues);
        }
    }

    /**
     * Returns the current settings for this environment.
     *
     * @param templateName
     *            If not null, get the settings for the template named, rather
     *            than the running environment.
     */
    public Map<String, List<ConfigurationOptionSetting>> getCurrentSettings(String templateName) {
        Map<String, List<ConfigurationOptionSetting>> settings;
        if ( templateName == null )
            settings = getEnvironmentConfiguration(environment.getEnvironmentName());
        else
            settings = getTemplateConfiguration(templateName);
        return settings;
    }

    /**
     * Returns a list of configuration options sorted first by namespace, then
     * by option name.
     */
    public List<ConfigurationOptionDescription> getSortedConfigurationOptions() {
        AWSElasticBeanstalk client = AwsToolkitCore.getClientFactory(environment.getAccountId())
                .getElasticBeanstalkClientByEndpoint(environment.getRegionEndpoint());

        DescribeConfigurationOptionsResult optionsDesc = null;
        try {
            optionsDesc = client.describeConfigurationOptions(new DescribeConfigurationOptionsRequest()
                .withEnvironmentName(environment.getEnvironmentName()));
        } catch (AmazonServiceException e) {
            if ( "InvalidParameterValue".equals(e.getErrorCode()) ) {
                // If the environment doesn't exist yet...
                return new ArrayList<ConfigurationOptionDescription>();
            } else {
                throw e;
            }
        }

        List<ConfigurationOptionDescription> options = optionsDesc.getOptions();
        Collections.sort(options, new Comparator<ConfigurationOptionDescription>() {

            public int compare(ConfigurationOptionDescription o1, ConfigurationOptionDescription o2) {
                if ( o1.getNamespace().equals(o2.getNamespace()) ) {
                    return o1.getName().compareTo(o2.getName());
                } else {
                    return o1.getNamespace().compareTo(o2.getNamespace());
                }
            }
        });
        return options;
    }

    /**
     * Returns map of configuration option settings for an environment keyed by
     * their namespace.
     */
    public Map<String, List<ConfigurationOptionSetting>> getEnvironmentConfiguration(String environmentName) {
        try {
            List<ConfigurationSettingsDescription> settings = environment.getCurrentSettings();
            if ( settings.isEmpty() ) return null;
            return createSettingsMap(settings);
        } catch (AmazonServiceException ase) {
            if (ase.getErrorCode().equals("InvalidParameterValue")) return null;
            else throw ase;
        }
    }

    /**
     * Returns map of configuration option settings for a template keyed by
     * their namespace.
     */
    public Map<String, List<ConfigurationOptionSetting>> getTemplateConfiguration(String templateName) {
        List<ConfigurationSettingsDescription> settings = environment.getCurrentSettings();

        if ( settings.isEmpty() )
            return null;

        return createSettingsMap(settings);
    }

    /**
     * Sorts the list of settings given into a map keyed by namespace.
     */
    public Map<String, List<ConfigurationOptionSetting>> createSettingsMap(List<ConfigurationSettingsDescription> settings) {
        Map<String, List<ConfigurationOptionSetting>> options = new HashMap<String, List<ConfigurationOptionSetting>>();
        for ( ConfigurationOptionSetting opt : settings.get(0).getOptionSettings() ) {
            if ( !options.containsKey(opt.getNamespace()) ) {
                options.put(opt.getNamespace(), new ArrayList<ConfigurationOptionSetting>());
            }
            options.get(opt.getNamespace()).add(opt);
        }
        return options;
    }

    /**
     * Transforms the model into a list of configuration option settings.
     */
    public Collection<ConfigurationOptionSetting> createConfigurationOptions() {
        Collection<ConfigurationOptionSetting> settings = new ArrayList<ConfigurationOptionSetting>();

        for ( Object key : dataModel.keySet() ) {
            OptionKey option = (OptionKey) key;
            if ( dataModel.get(key) instanceof Collection ) {
                @SuppressWarnings("rawtypes")
                Collection collection = (Collection) dataModel.get(key);
                if ( !collection.isEmpty() ) {
                    ConfigurationOptionSetting setting = new ConfigurationOptionSetting()
                            .withNamespace(option.namespace).withOptionName(option.name)
                            .withValue(join(collection));
                    settings.add(setting);
                }
            } else {
                ConfigurationOptionSetting setting = new ConfigurationOptionSetting()
                        .withNamespace(option.namespace).withOptionName(option.name)
                        .withValue(dataModel.get(key).toString());
                settings.add(setting);
            }
        }
        return settings;
    }

    /**
     * Joins the collection given with commas.
     */
    @SuppressWarnings("rawtypes")
    private String join(Collection list) {
        StringBuilder b = new StringBuilder();
        boolean seenOne = false;
        for ( Object o : list ) {
            if ( seenOne )
                b.append(",");
            else
                seenOne = true;
            b.append(o);
        }
        return b.toString();
    }

    private OptionKey getKey(ConfigurationOptionDescription key) {
        return new OptionKey(key);
    }

    /**
     * Cancels the thread given if it's running.
     */
    protected void cancelThread(CancelableThread thread) {
        if ( thread != null ) {
            synchronized (thread) {
                if ( thread.isRunning() ) {
                    thread.cancel();
                }
            }
        }
    }

    private final class RefreshThread extends CancelableThread {

        private String template;

        public RefreshThread(String template) {
            this.template = template;
        }

        @Override
        public void run() {
            try {
                for ( RefreshListener listener : listeners ) {
                    listener.refreshStarted();
                }

                final List<ConfigurationOptionDescription> options = getSortedConfigurationOptions();
                final Map<String, List<ConfigurationOptionSetting>> settings = getCurrentSettings(template);

                Display.getDefault().syncExec(new Runnable() {

                    public void run() {
                        synchronized (RefreshThread.this) {
                            if ( !isCanceled() )
                                init(settings, options);
                        }
                    }
                });
            } catch ( Exception e ) {
                for ( RefreshListener listener : listeners ) {
                    listener.refreshError(e);
                }
                return;
            }

            for ( RefreshListener listener : listeners ) {
                listener.refreshFinished();
            }
        }
    }

    /**
     * We can't use the SDK's data types as map keys because they don't .equals
     * one another. This is a lightweight adapter to allow them to be used as
     * such, as well as being reversible to an option name.
     */
    private static final class OptionKey {

        private final String name;
        private final String namespace;

        public OptionKey(ConfigurationOptionDescription opt) {
            this.name = opt.getName();
            this.namespace = opt.getNamespace();
        }

        @Override
        public boolean equals(Object o2) {
            if ( o2 instanceof OptionKey == false )
                return false;
            return ((OptionKey) o2).name.equals(this.name) && ((OptionKey) o2).namespace.equals(this.namespace);
        }

        @Override
        public int hashCode() {
            return name.hashCode() + namespace.hashCode();
        }
    }

}
