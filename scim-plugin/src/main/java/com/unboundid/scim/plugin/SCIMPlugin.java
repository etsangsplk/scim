/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.plugin;



import com.unboundid.directory.sdk.common.types.LogSeverity;
import com.unboundid.directory.sdk.ds.api.Plugin;
import com.unboundid.directory.sdk.ds.config.PluginConfig;
import com.unboundid.directory.sdk.ds.types.DirectoryServerContext;
import com.unboundid.directory.sdk.ds.types.StartupPluginResult;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldif.LDIFException;
import com.unboundid.scim.ri.SCIMServer;
import com.unboundid.scim.ri.SCIMServerConfig;
import com.unboundid.scim.ri.wink.SCIMApplication;
import com.unboundid.scim.sdk.Debug;
import com.unboundid.scim.sdk.DebugType;
import com.unboundid.scim.sdk.SCIMBackend;
import com.unboundid.util.StaticUtils;
import com.unboundid.util.args.ArgumentException;
import com.unboundid.util.args.ArgumentParser;
import com.unboundid.util.args.BooleanArgument;
import com.unboundid.util.args.FileArgument;
import com.unboundid.util.args.IntegerArgument;
import com.unboundid.util.args.StringArgument;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;

import static com.unboundid.scim.sdk.Debug.debugException;



/**
 * This class provides a plugin that presents a Simple Cloud Identity
 * Management (SCIM) protocol interface to the Directory Server.
 */
public final class SCIMPlugin
       extends Plugin
{
  /**
   * The name of the argument that will be used to define the resources
   * supported by the SCIM protocol interface.
   */
  private static final String ARG_NAME_USE_RESOURCES_FILE = "useResourcesFile";

  /**
   * The name of the argument that will be used to specify the port number of
   * the SCIM protocol interface.
   */
  private static final String ARG_NAME_PORT = "port";

  /**
   * The name of the argument that will be used to specify the base URI of
   * the SCIM protocol interface.
   */
  private static final String ARG_NAME_BASE_URI = "baseURI";

  /**
   * The name of the argument that will be used to enable debug logging in the
   * SCIM plugin.
   */
  private static final String ARG_NAME_DEBUG_ENABLED = "debugEnabled";

  /**
   * The name of the argument that will be used to specify the level of debug
   * logging in the SCIM plugin.
   */
  private static final String ARG_NAME_DEBUG_LEVEL = "debugLevel";

  /**
   * The name of the argument that will be used to specify the types of debug
   * logging in the SCIM plugin.
   */
  private static final String ARG_NAME_DEBUG_TYPE = "debugType";

  /**
   * The name of the argument that will be used to specify the maximum number
   * of resources returned in a response.
   */
  private static final String ARG_NAME_MAX_RESULTS = "maxResults";

  /**
   * The singleton instance of this plugin.
   */
  private static volatile SCIMPlugin INSTANCE;

  /**
   * The server context for the server in which this extension is running.
   */
  private DirectoryServerContext serverContext;

  /**
   * The backend that will handle the SCIM requests.
   */
  private SCIMBackend scimBackend;

  /**
   * The JAX-RS application that will handle the SCIM requests.
   */
  private volatile SCIMApplication scimApplication;

  /**
   * Creates a new instance of this plugin.  All plugin implementations must
   * include a default constructor, but any initialization should generally be
   * done in the {@code initializePlugin} method.
   */
  public SCIMPlugin()
  {
    // No implementation required.
  }



  /**
   * Retrieves a human-readable name for this extension.
   *
   * @return  A human-readable name for this extension.
   */
  @Override()
  public String getExtensionName()
  {
    return "SCIM Plugin";
  }



  /**
   * Retrieves a human-readable description for this extension.  Each element
   * of the array that is returned will be considered a separate paragraph in
   * generated documentation.
   *
   * @return  A human-readable description for this extension, or {@code null}
   *          or an empty array if no description should be available.
   */
  @Override()
  public String[] getExtensionDescription()
  {
    return new String[]
    {
      "This plugin provides a Simple Cloud Identity Management (SCIM) " +
      "protocol interface to the Directory Server."
    };
  }



  /**
   * Updates the provided argument parser to define any configuration arguments
   * which may be used by this plugin.  The argument parser may also be updated
   * to define relationships between arguments (e.g., to specify required,
   * exclusive, or dependent argument sets).
   *
   * @param  parser  The argument parser to be updated with the configuration
   *                 arguments which may be used by this plugin.
   *
   * @throws  ArgumentException  If a problem is encountered while updating the
   *                             provided argument parser.
   */
  @Override()
  public void defineConfigArguments(final ArgumentParser parser)
         throws ArgumentException
  {
    // This is a required argument.
    parser.addArgument(
        new FileArgument(null, ARG_NAME_USE_RESOURCES_FILE,
                         true, 1, "{path}",
                         "The path to an XML file defining the resources " +
                         "supported by the SCIM interface.",
                         true, true, true, false));

    // This is a required argument.
    parser.addArgument(
        new IntegerArgument(null, ARG_NAME_PORT,
                            true, 1, "{port}",
                            "The port number of the SCIM interface.",
                            0, 65535));

    // This argument is required but has a default.
    parser.addArgument(
        new StringArgument(null, ARG_NAME_BASE_URI,
                           true, 1, "{URL-path}",
                           "The base URI of the SCIM interface. If no base " +
                           "URI is specified then the default value '/' is " +
                           "used", "/"));

    // Debug log arguments.
    parser.addArgument(
        new BooleanArgument(null, ARG_NAME_DEBUG_ENABLED,
                           "Enables debug logging in the SCIM plugin"));

    parser.addArgument(
        new StringArgument(null, ARG_NAME_DEBUG_LEVEL,
                           false, 1,
                           "(SEVERE|WARN|INFO|CONFIG|FINE|FINER|FINEST)",
                           "Specifies the level of debug logging in the SCIM" +
                           " plugin"));

    parser.addArgument(
        new StringArgument(null, ARG_NAME_DEBUG_TYPE,
                           false, 1,
                           "{debug-type,...}",
                           "Specifies the types of debug logging in the SCIM" +
                           " plugin"));

    // This argument has a default.
    parser.addArgument(
        new IntegerArgument(null, ARG_NAME_MAX_RESULTS,
                            true, 1, "{integer}",
                            "The maximum number of resources that are " +
                            "returned in a response. The default value is 100",
                            1, Integer.MAX_VALUE, 100));

  }



  /**
   * Initializes this plugin.
   *
   * @param  serverContext  A handle to the server context for the server in
   *                        which this extension is running.
   * @param  config         The general configuration for this plugin.
   * @param  parser         The argument parser which has been initialized from
   *                        the configuration for this plugin.
   *
   * @throws  LDAPException  If a problem occurs while initializing this plugin.
   */
  @Override()
  public void initializePlugin(final DirectoryServerContext serverContext,
                               final PluginConfig config,
                               final ArgumentParser parser)
      throws LDAPException
  {
    Debug.getLogger().addHandler(new DebugLogHandler(serverContext));
    Debug.getLogger().setUseParentHandlers(false);

    final BooleanArgument debugEnabledArg =
        (BooleanArgument) parser.getNamedArgument(ARG_NAME_DEBUG_ENABLED);
    final StringArgument debugLevelArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_DEBUG_LEVEL);
    final StringArgument debugTypeArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_DEBUG_TYPE);

    final Properties properties = new Properties();
    properties.setProperty(Debug.PROPERTY_DEBUG_ENABLED,
                           Boolean.toString(debugEnabledArg.isPresent()));
    if (debugLevelArg.isPresent())
    {
      properties.setProperty(Debug.PROPERTY_DEBUG_LEVEL,
                             debugLevelArg.getValue());
    }
    if (debugTypeArg.isPresent())
    {
      properties.setProperty(Debug.PROPERTY_DEBUG_TYPE,
                             debugTypeArg.getValue());
    }
    Debug.initialize(properties);

    Debug.debug(Level.INFO, DebugType.OTHER,
                "Beginning SCIM plugin initialization");

    this.serverContext = serverContext;

    final SCIMServerConfig scimServerConfig = getSCIMConfig(parser);

    final SCIMServer scimServer = SCIMServer.getInstance();
    try
    {
      scimServer.initializeServer(scimServerConfig);
    }
    catch (Exception e)
    {
      debugException(e);
      throw new LDAPException(
          ResultCode.OTHER,
          "An error occurred while initializing the SCIM plugin.", e);
    }

    final StringArgument baseUriArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_BASE_URI);
    final IntegerArgument maxResultsArg =
         (IntegerArgument) parser.getNamedArgument(ARG_NAME_MAX_RESULTS);

    final String baseUri = baseUriArg.getValue();
    scimBackend = new ServerContextBackend(serverContext);
    scimBackend.getConfig().setMaxResults(maxResultsArg.getValue());
    scimApplication = scimServer.registerBackend(baseUri, scimBackend);

    if (serverContext.isRunning())
    {
      // This is needed to start listening if the plugin was dynamically added
      // after the server was started (e.g., via dsconfig).
      try
      {
        scimServer.startListening();
        serverContext.logMessage(
            LogSeverity.NOTICE,
            "The server is listening for SCIM requests on port " +
            scimServer.getListenPort());
      }
      catch (final Exception e)
      {
        debugException(e);
        throw new LDAPException(
            ResultCode.OTHER,
            "An error occurred while attempting to start listening for " +
            "SCIM requests:" + StaticUtils.getExceptionMessage(e), e);
      }

      addMonitorProvider();
    }

    Debug.debug(Level.INFO, DebugType.OTHER,
                "Finished SCIM plugin initialization");

    // Set the instance singleton to this initialized instance.
    INSTANCE = this;
  }



  /**
   * Indicates whether the configuration contained in the provided argument
   * parser represents a valid configuration for this extension.
   *
   * @param  config               The general configuration for this plugin.
   * @param  parser               The argument parser which has been initialized
   *                              with the proposed configuration.
   * @param  unacceptableReasons  A list that can be updated with reasons that
   *                              the proposed configuration is not acceptable.
   *
   * @return  {@code true} if the proposed configuration is acceptable, or
   *          {@code false} if not.
   */
  @Override()
  public boolean isConfigurationAcceptable(final PluginConfig config,
                      final ArgumentParser parser,
                      final List<String> unacceptableReasons)
  {
    boolean acceptable = true;

    final StringArgument debugLevelArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_DEBUG_LEVEL);
    final StringArgument debugTypeArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_DEBUG_TYPE);

    if (debugLevelArg.isPresent())
    {
      try
      {
        Level.parse(debugLevelArg.getValue());
      }
      catch (IllegalArgumentException e)
      {
        debugException(e);
        unacceptableReasons.add("Invalid value '" + debugLevelArg.getValue() +
                                "' for the SCIM plugin debug level");
        acceptable = false;
      }
    }

    if (debugTypeArg.isPresent())
    {
      final StringTokenizer t =
          new StringTokenizer(debugTypeArg.getValue(), ", ");
      while (t.hasMoreTokens())
      {
        final String debugTypeName = t.nextToken();
        if (DebugType.forName(debugTypeName) == null)
        {
          unacceptableReasons.add(
              "Invalid value '" + debugTypeName +
              "' for a SCIM plugin debug type.  Allowed values include:  " +
              DebugType.getTypeNameList() + '.');
          acceptable = false;
        }
      }
    }

    final SCIMServerConfig scimServerConfig = getSCIMConfig(parser);
    if (!SCIMServer.getInstance().isConfigAcceptable(scimServerConfig,
                                                     unacceptableReasons))
    {
      acceptable = false;
    }

    return acceptable;
  }



  /**
   * Attempts to apply the configuration contained in the provided argument
   * parser.
   *
   * @param  config                The general configuration for this plugin.
   * @param  parser                The argument parser which has been
   *                               initialized with the new configuration.
   * @param  adminActionsRequired  A list that can be updated with information
   *                               about any administrative actions that may be
   *                               required before one or more of the
   *                               configuration changes will be applied.
   * @param  messages              A list that can be updated with information
   *                               about the result of applying the new
   *                               configuration.
   *
   * @return  A result code that provides information about the result of
   *          attempting to apply the configuration change.
   */
  @Override()
  public ResultCode applyConfiguration(final PluginConfig config,
                                       final ArgumentParser parser,
                                       final List<String> adminActionsRequired,
                                       final List<String> messages)
  {
    ResultCode rc = ResultCode.SUCCESS;

    final BooleanArgument debugEnabledArg =
        (BooleanArgument) parser.getNamedArgument(ARG_NAME_DEBUG_ENABLED);
    final StringArgument debugLevelArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_DEBUG_LEVEL);
    final StringArgument debugTypeArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_DEBUG_TYPE);

    final Properties properties = new Properties();
    properties.setProperty(Debug.PROPERTY_DEBUG_ENABLED,
                           Boolean.toString(debugEnabledArg.isPresent()));
    if (debugLevelArg.isPresent())
    {
      properties.setProperty(Debug.PROPERTY_DEBUG_LEVEL,
                             debugLevelArg.getValue());
    }
    if (debugTypeArg.isPresent())
    {
      properties.setProperty(Debug.PROPERTY_DEBUG_TYPE,
                             debugTypeArg.getValue());
    }
    Debug.initialize(properties);

    final SCIMServer scimServer = SCIMServer.getInstance();
    try
    {
      scimServer.shutdown();
    }
    catch (Exception e)
    {
      debugException(e);
    }

    final SCIMServerConfig scimServerConfig = getSCIMConfig(parser);

    try
    {
      scimServer.initializeServer(scimServerConfig);
    }
    catch (Exception e)
    {
      debugException(e);
      messages.add("An error occurred while initializing the SCIM plugin:" +
                   StaticUtils.getExceptionMessage(e));
      return ResultCode.OTHER;
    }

    final StringArgument baseUriArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_BASE_URI);
    final IntegerArgument maxResultsArg =
         (IntegerArgument) parser.getNamedArgument(ARG_NAME_MAX_RESULTS);

    final String baseUri = baseUriArg.getValue();
    scimBackend = new ServerContextBackend(serverContext);
    scimBackend.getConfig().setMaxResults(maxResultsArg.getValue());
    scimApplication = scimServer.registerBackend(baseUri, scimBackend);

    try
    {
      scimServer.startListening();
      serverContext.logMessage(
          LogSeverity.NOTICE,
          "The server is listening for SCIM requests on port " +
          scimServer.getListenPort());
    }
    catch (final Exception e)
    {
      debugException(e);
      messages.add("An error occurred while attempting to start listening " +
                   "for SCIM requests:" + StaticUtils.getExceptionMessage(e));
      return ResultCode.OTHER;
    }

    return rc;
  }



  /**
   * Performs any processing which may be necessary when the server is starting.
   *
   * @return  Information about the result of the plugin processing.
   */
  public StartupPluginResult doStartup()
  {
    // Start listening for SCIM requests.
    try
    {
      final SCIMServer scimServer = SCIMServer.getInstance();
      scimServer.startListening();
      serverContext.logMessage(
          LogSeverity.NOTICE,
          "The server is listening for SCIM requests on port " +
          scimServer.getListenPort());
    }
    catch (final Exception e)
    {
      debugException(e);
      return new StartupPluginResult(
          false, true,
          "An error occurred while attempting to start listening for " +
          "SCIM requests:" + StaticUtils.getExceptionMessage(e));
    }

    addMonitorProvider();

    return StartupPluginResult.SUCCESS;
  }



  /**
   * Performs any processing which may be necessary when the server is shutting
   * down.
   *
   * @param  shutdownReason  A message which may provide information about the
   *                         reason the server is shutting down.
   */
  public void doShutdown(final String shutdownReason)
  {
    try
    {
      SCIMServer.getInstance().shutdown();
    }
    catch (Exception e)
    {
      debugException(e);
    }
  }



  /**
   * Performs any cleanup which may be necessary when this plugin is to be taken
   * out of service.
   */
  @Override()
  public void finalizePlugin()
  {
    try
    {
      SCIMServer.getInstance().shutdown();
    }
    catch (Exception e)
    {
      debugException(e);
    }
  }



  /**
   * Retrieves a map containing examples of configurations that may be used for
   * this extension.  The map key should be a list of sample arguments, and the
   * corresponding value should be a description of the behavior that will be
   * exhibited by the extension when used with that configuration.
   *
   * @return  A map containing examples of configurations that may be used for
   *          this extension.  It may be {@code null} or empty if there should
   *          not be any example argument sets.
   */
  @Override()
  public Map<List<String>,String> getExamplesArgumentSets()
  {
    final LinkedHashMap<List<String>,String> exampleMap =
         new LinkedHashMap<List<String>,String>(1);

    exampleMap.put(
         Arrays.asList(
              ARG_NAME_USE_RESOURCES_FILE + "=config/resources.xml",
              ARG_NAME_PORT + "=8080"),
         "Creates a SCIM protocol interface listening on port 8080. The " +
         "interface supports the resources defined in resources.xml.");

    return exampleMap;
  }



  /**
   * Creates a SCIM server configuration based on information provided in an
   * argument parser.
   *
   * @param parser  The argument parser containing the information needed to
   *                create the configuration.
   *
   * @return  The configuration that was created.
   *
   */
  private static SCIMServerConfig getSCIMConfig(final ArgumentParser parser)
  {
    final FileArgument useResourcesFileArg =
         (FileArgument) parser.getNamedArgument(ARG_NAME_USE_RESOURCES_FILE);

    final IntegerArgument portArg =
         (IntegerArgument) parser.getNamedArgument(ARG_NAME_PORT);

    final SCIMServerConfig scimServerConfig = new SCIMServerConfig();

    scimServerConfig.setListenPort(portArg.getValue());
    scimServerConfig.setResourcesFile(useResourcesFileArg.getValue());

    return scimServerConfig;
  }



  /**
   * Add a configuration entry for the SCIM monitor provider if there is not
   * one already.
   */
  private void addMonitorProvider()
  {
    try
    {
      serverContext.getInternalRootConnection().add(
          "dn: cn=SCIM Monitor Provider,cn=Monitor Providers,cn=config",
          "objectClass: top",
          "objectClass: ds-cfg-monitor-provider",
          "objectClass: ds-cfg-third-party-monitor-provider",
          "cn: SCIM Monitor Provider",
          "ds-cfg-java-class: " +
          "com.unboundid.directory.sdk.extensions.ThirdPartyMonitorProvider",
          "ds-cfg-extension-class: " +
          "com.unboundid.scim.plugin.SCIMMonitorProvider",
          "ds-cfg-enabled: true"
      );
    }
    catch (LDIFException e)
    {
      debugException(e);
    }
    catch (LDAPException e)
    {
      if (!e.getResultCode().equals(ResultCode.ENTRY_ALREADY_EXISTS))
      {
        debugException(e);
      }
    }
  }

  /**
   * Retrieves the SCIM JAX-RS application instance used by this plugin.
   *
   * @return The SCIM JAX-RS application instance used by this plugin.
   */
  SCIMApplication getSCIMApplication()
  {
    return scimApplication;
  }

  /**
   * Retrieves the singleton instance of this SCIMPlugin.
   *
   * @return The singleton instance of this SCIMPlugin.
   */
  static SCIMPlugin getInstance()
  {
    return INSTANCE;
  }
}
