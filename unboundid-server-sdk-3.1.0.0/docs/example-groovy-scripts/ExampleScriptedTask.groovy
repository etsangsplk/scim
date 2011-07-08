/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * docs/licenses/cddl.txt
 * or http://www.opensource.org/licenses/cddl1.php.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * docs/licenses/cddl.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010-2011 UnboundID Corp.
 */
package com.unboundid.directory.sdk.examples.groovy;



import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unboundid.directory.sdk.common.types.InternalConnection;
import com.unboundid.directory.sdk.common.types.LogSeverity;
import com.unboundid.directory.sdk.ds.scripting.ScriptedTask;
import com.unboundid.directory.sdk.ds.types.DirectoryServerContext;
import com.unboundid.directory.sdk.ds.types.TaskContext;
import com.unboundid.directory.sdk.ds.types.TaskReturnState;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchResultListener;
import com.unboundid.ldap.sdk.SearchResultReference;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldif.LDIFWriter;
import com.unboundid.util.StaticUtils;
import com.unboundid.util.args.ArgumentException;
import com.unboundid.util.args.ArgumentParser;
import com.unboundid.util.args.DNArgument;
import com.unboundid.util.args.FileArgument;
import com.unboundid.util.args.FilterArgument;
import com.unboundid.util.args.StringArgument;



/**
 * This class provides a simple example of a scripted task that will perform an
 * internal search and write the results to a file on the server filesystem.
 * The search criteria will be specified using the provided arguments:
 * <UL>
 *   <LI>base-dn -- The base DN for the search to perform.</LI>
 *   <LI>scope -- The scope for the search.  The value should be one of the
 *       following strings:  base, one, sub, or subordinate-subtree.</LI>
 *   <LI>filter -- The filter for the search.</LI>
 *   <LI>attribute -- An attribute to request.  This may be provided multiple
 *       times to request multiple attributes.</LI>
 *   <LI>output-file -- The path to the file to which the search results should
 *       be written.</LI>
 * </UL>
 */
public final class ExampleScriptedTask
       extends ScriptedTask
       implements SearchResultListener
{
  /**
   * The name of the argument used to specify the search base DN.
   */
  private static final String ARG_NAME_BASE_DN = "base-dn";



  /**
   * The name of the argument used to specify the search scope.
   */
  private static final String ARG_NAME_SCOPE = "scope";



  /**
   * The name of the argument used to specify the filter.
   */
  private static final String ARG_NAME_FILTER = "filter";



  /**
   * The name of the argument used to specify the requested attributes.
   */
  private static final String ARG_NAME_ATTR = "attribute";



  /**
   * The name of the argument used to specify the path to the output file.
   */
  private static final String ARG_NAME_OUTPUT_FILE = "output-file";



  /**
   * The map of search scope names to values.
   */
  private static final Map<String,SearchScope> SCOPE_MAP;



  /**
   * The set of allowed search scope values.
   */
  private static final Set<String> ALLOWED_SCOPES;



  static
  {
    final LinkedHashMap<String,SearchScope> scopeMap =
         new LinkedHashMap<String,SearchScope>(4);
    scopeMap.put("base", SearchScope.BASE);
    scopeMap.put("one", SearchScope.ONE);
    scopeMap.put("sub", SearchScope.SUB);
    scopeMap.put("subordinate-subtree", SearchScope.SUBORDINATE_SUBTREE);

    SCOPE_MAP = Collections.unmodifiableMap(scopeMap);
    ALLOWED_SCOPES = Collections.unmodifiableSet(scopeMap.keySet());
  }



  /**
   * The serial version UID for this serializable class.
   */
  private static final long serialVersionUID = -7584765913920729867L;



  // The server context for the server in which this extension is running.
  private DirectoryServerContext serverContext;

  // The output file for the search results.
  private File outputFile;

  // The filter for the search.
  private Filter filter;

  // The LDIF writer to use when writing the results.
  private volatile LDIFWriter ldifWriter;

  // The list of requested attributes.
  private List<String> requestedAttributes;

  // The scope for the search.
  private SearchScope scope;

  // The base DN for the search.
  private String baseDN;

  // The task context for the task.
  private volatile TaskContext taskContext;

  // The return state for the task.
  private volatile TaskReturnState returnState;



  /**
   * Creates a new instance of this task.  All task implementations must
   * include a default constructor, but any initialization should generally be
   * done in the {@code initializeTask} method.
   */
  public ExampleScriptedTask()
  {
    // No implementation required.
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
    // Add an argument that allows you to specify the search base DN.
    Character shortIdentifier = null;
    String    longIdentifier  = ARG_NAME_BASE_DN;
    boolean   required        = true;
    int       maxOccurrences  = 1;
    String    placeholder     = "{dn}";
    String    description     = "The base DN to use for the search.";

    parser.addArgument(new DNArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description));


    // Add an argument that allows you to specify the search scope.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_SCOPE;
    required        = true;
    maxOccurrences  = 1;
    placeholder     = "{base|one|sub|subordinate-subtree}";
    description     = "The scope to use for the search.";

    parser.addArgument(new StringArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description,
         ALLOWED_SCOPES));


    // Add an argument that allows you to specify the search filter.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_FILTER;
    required        = true;
    maxOccurrences  = 1;
    placeholder     = "{filter}";
    description     = "The filter to use for the search.";

    parser.addArgument(new FilterArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description));


    // Add an argument that allows you to specify the requested attributes.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_ATTR;
    required        = false;
    maxOccurrences  = 0; // No limit
    placeholder     = "{attr}";
    description     = "An attribute to include in matching entries.  This " +
         "argument may be provided multiple times to request multiple " +
         "attributes.  If no requested attributes are provided, then all " +
         "user attributes will be requested.";

    parser.addArgument(new StringArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description));


    // Add an argument that allows you to specify the output file.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_OUTPUT_FILE;
    required        = true;
    maxOccurrences  = 1;
    placeholder     = "{path}";
    description     = "The output file to which the search results should be " +
         "written.  Relative paths will be relative to the server root.";

    boolean fileMustExist   = false;
    boolean parentMustExist = true;
    boolean mustBeFile      = true;
    boolean mustBeDirectory = false;

    parser.addArgument(new FileArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description, fileMustExist,
         parentMustExist, mustBeFile, mustBeDirectory));
  }



  /**
   * Initializes this task.
   *
   * @param  serverContext  A handle to the server context for the server in
   *                        which this task is running.
   * @param  parser         The argument parser which has been initialized from
   *                        the configuration for this task.
   *
   * @throws  LDAPException  If a problem occurs while initializing this task.
   */
  @Override()
  public void initializeTask(final DirectoryServerContext serverContext,
                             final ArgumentParser parser)
         throws LDAPException
  {
    this.serverContext = serverContext;


    // Get the base DN.
    final DNArgument baseArg =
         (DNArgument) parser.getNamedArgument(ARG_NAME_BASE_DN);
    baseDN = baseArg.getValue().toString();


    // Get the scope.
    final StringArgument scopeArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_SCOPE);
    scope = SCOPE_MAP.get(StaticUtils.toLowerCase(scopeArg.getValue()));


    // Get the filter.
    final FilterArgument filterArg =
         (FilterArgument) parser.getNamedArgument(ARG_NAME_FILTER);
    filter = filterArg.getValue();


    // Get the list of requested attributes.
    final StringArgument attrsArg =
         (StringArgument) parser.getNamedArgument(ARG_NAME_ATTR);
    requestedAttributes = attrsArg.getValues();


    // Get the output file.
    final FileArgument outputFileArg =
         (FileArgument) parser.getNamedArgument(ARG_NAME_OUTPUT_FILE);
    outputFile = outputFileArg.getValue();
  }



  /**
   * Performs the appropriate processing for this task.
   *
   * @param  taskContext  Information about the task to be run.
   *
   * @return  Information about the state of the task after processing has
   *          completed.
   */
  @Override()
  public TaskReturnState runTask(final TaskContext taskContext)
  {
    this.taskContext = taskContext;

    // Create an LDIF writer to use to write the results.
    try
    {
      ldifWriter = new LDIFWriter(outputFile);
    }
    catch (final Exception e)
    {
      serverContext.debugCaught(e);
      serverContext.logMessage(LogSeverity.SEVERE_ERROR,
           "Unable to create an LDIF writer to write to file " +
                outputFile.getAbsolutePath() + " for task " +
                taskContext.getTaskEntryDN() + ":  " +
                StaticUtils.getExceptionMessage(e));
      return TaskReturnState.STOPPED_BY_ERROR;
    }

    try
    {
      // Append a header to the LDIF file with details of the search.
      try
      {
        ldifWriter.writeComment("Internal search initiated at " +
                  new Date().toString() + " for task " +
                  taskContext.getTaskEntryDN(),
             false, false);
        ldifWriter.writeComment("Base DN:  " + baseDN, false, false);
        ldifWriter.writeComment("Scope:  " + scope.getName(), false, false);
        ldifWriter.writeComment("Filter:  " + filter.toString(), false, false);
        ldifWriter.writeComment("Requested Attributes:  " + requestedAttributes,
             false, true);
      }
      catch (final Exception e)
      {
        serverContext.debugCaught(e);
        serverContext.logMessage(LogSeverity.SEVERE_ERROR,
             "Unable to write a header to LDIF file " +
                  outputFile.getAbsolutePath() + ":  " +
                  StaticUtils.getExceptionMessage(e));
        return TaskReturnState.STOPPED_BY_ERROR;
      }


      // Create the search request to issue.  We'll use a search result listener
      // since the search may return a large number of entries.
      final SearchRequest searchRequest = new SearchRequest(this, baseDN, scope,
           filter);
      searchRequest.setAttributes(requestedAttributes);


      // Get an internal connection and issue the search.
      returnState = TaskReturnState.COMPLETED_SUCCESSFULLY;
      SearchResult searchResult;
      try
      {
        final InternalConnection conn =
             serverContext.getInternalRootConnection();
        searchResult = conn.search(searchRequest);
      }
      catch (final LDAPSearchException lse)
      {
        serverContext.debugCaught(lse);

        searchResult = lse.getSearchResult();
        if (returnState == TaskReturnState.COMPLETED_SUCCESSFULLY)
        {
          returnState = TaskReturnState.COMPLETED_WITH_ERRORS;
        }

        serverContext.logMessage(LogSeverity.MILD_ERROR,
             "An error occurred while processing the search for task " +
                  taskContext.getTaskEntryDN() + ":  " +
                  searchResult.toString());
      }


      // Append a footer to the LDIF file with a summary of the results.
      try
      {
        ldifWriter.writeComment("Internal search completed at " +
                  new Date().toString(),
             true, false);
        ldifWriter.writeComment("Result Code:  " +
                  searchResult.getResultCode().toString(),
             false, false);

        final String diagnosticMessage = searchResult.getDiagnosticMessage();
        if (diagnosticMessage != null)
        {
          ldifWriter.writeComment("Diagnostic Message:  " + diagnosticMessage,
               false, false);
        }

        final String matchedDN = searchResult.getMatchedDN();
        if (matchedDN != null)
        {
          ldifWriter.writeComment("Matched DN:  " + matchedDN,
               false, false);
        }

        final String[] referralURLs = searchResult.getReferralURLs();
        if ((referralURLs != null) && (referralURLs.length > 0))
        {
          ldifWriter.writeComment("Referral URLs:  " +
                    Arrays.toString(referralURLs),
               false, false);
        }

        ldifWriter.writeComment("Entries Returned:  " +
                  searchResult.getEntryCount(),
             false, false);
        ldifWriter.writeComment("References Returned:  " +
                  searchResult.getReferenceCount(),
             false, false);
      }
      catch (final Exception e)
      {
        serverContext.debugCaught(e);
        serverContext.logMessage(LogSeverity.SEVERE_ERROR,
             "Unable to write a header to LDIF file " +
                  outputFile.getAbsolutePath() + ":  " +
                  StaticUtils.getExceptionMessage(e));
        return TaskReturnState.STOPPED_BY_ERROR;
      }

      return returnState;
    }
    finally
    {
      try
      {
        ldifWriter.close();
      }
      catch (final Exception e)
      {
        serverContext.debugCaught(e);
        serverContext.logMessage(LogSeverity.SEVERE_ERROR,
             "An error occurred while closing file " +
                  outputFile.getAbsolutePath() + " for task " +
                  taskContext.getTaskEntryDN() + ":  " +
                  StaticUtils.getExceptionMessage(e));
      }
    }
  }



  /**
   * Indicates that the provided search result entry has been returned by the
   * server and may be processed by this search result listener.
   *
   * @param  searchEntry  The search result entry that has been returned by the
   *                      server.
   */
  public void searchEntryReturned(final SearchResultEntry searchEntry)
  {
    try
    {
      ldifWriter.writeEntry(searchEntry);
    }
    catch (final Exception e)
    {
      serverContext.debugCaught(e);
      serverContext.logMessage(LogSeverity.SEVERE_ERROR,
           "An error occurred while attempting to write matching entry " +
                searchEntry.getDN() + " to file " +
                outputFile.getAbsolutePath() + " for task " +
                taskContext.getTaskEntryDN() + ":  " +
                StaticUtils.getExceptionMessage(e));
      returnState = TaskReturnState.COMPLETED_WITH_ERRORS;
    }
  }



  /**
   * Indicates that the provided search result reference has been returned by
   * the server and may be processed by this search result listener.
   *
   * @param  searchReference  The search result reference that has been returned
   *                          by the server.
   */
  public void searchReferenceReturned(
                   final SearchResultReference searchReference)
  {
    try
    {
      ldifWriter.writeComment(
           "Received search result reference with URLs " +
                Arrays.toString(searchReference.getReferralURLs()),
           true, true);
    }
    catch (final Exception e)
    {
      serverContext.debugCaught(e);
      serverContext.logMessage(LogSeverity.SEVERE_ERROR,
           "An error occurred while attempting to information about " +
                "search result reference " +
                Arrays.toString(searchReference.getReferralURLs()) +
                " to file " + outputFile.getAbsolutePath() + " for task " +
                taskContext.getTaskEntryDN() + ":  " +
                StaticUtils.getExceptionMessage(e));
      returnState = TaskReturnState.COMPLETED_WITH_ERRORS;
    }
  }
}