/*
 * Sonar C# Plugin :: FxCop
 * Copyright (C) 2010 Jose Chillan, Alexandre Victoor and SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.csharp.fxcop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.SMEvent;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchExtension;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.rules.RuleQuery;
import org.sonar.api.rules.Violation;
import org.sonar.api.utils.SonarException;
import org.sonar.dotnet.tools.commons.visualstudio.VisualStudioProject;
import org.sonar.dotnet.tools.commons.visualstudio.VisualStudioSolution;
import org.sonar.plugins.csharp.api.CSharpResourcesBridge;
import org.sonar.plugins.csharp.api.MicrosoftWindowsEnvironment;
import org.sonar.plugins.csharp.api.ResourceHelper;
import org.sonar.plugins.csharp.core.AbstractStaxParser;

/**
 * Parses the reports generated by a FXCop analysis.
 */
public class FxCopResultParser extends AbstractStaxParser implements BatchExtension {

  private static final Logger LOG = LoggerFactory.getLogger(FxCopResultParser.class);
  private static final String NAMESPACE = "Namespace";
  private static final String NAMESPACES = "Namespaces";
  private static final String TARGETS = "Targets";
  private static final String MESSAGE = "Message";
  private static final String MESSAGES = "Messages";
  private static final String MODULE = "Module";
  private static final String NAME = "Name";
  private static final String TYPENAME = "TypeName";
  private static final String LINE = "Line";

  private final VisualStudioSolution vsSolution;
  private VisualStudioProject vsProject;
  private Project project;
  private SensorContext context;
  private RuleFinder ruleFinder;
  private CSharpResourcesBridge resourcesBridge;
  private ResourceHelper resourceHelper;
  private String repositoryKey;

  /**
   * Constructs a @link{FxCopResultParser}.
   * 
   * @param project
   * @param context
   * @param rulesManager
   * @param profile
   */
  public FxCopResultParser(MicrosoftWindowsEnvironment env, Project project, SensorContext context, RuleFinder ruleFinder,
      CSharpResourcesBridge resourcesBridge, ResourceHelper resourceHelper) {
    super();
    this.vsSolution = env.getCurrentSolution();
    if (vsSolution == null) {
      // not a C# project
      return;
    }
    this.vsProject = vsSolution.getProjectFromSonarProject(project);
    
    this.project = project;
    this.context = context;
    this.ruleFinder = ruleFinder;
    this.resourcesBridge = resourcesBridge;
    this.resourceHelper = resourceHelper;
  }

  /**
   * Parses a processed violation file.
   * 
   * @param file
   *          the file to parse
   */
  public void parse(File file) {
    
    this.repositoryKey = 
      vsProject.isTest() ? FxCopConstants.TEST_REPOSITORY_KEY : FxCopConstants.REPOSITORY_KEY;
    
    SMInputFactory inputFactory = initStax();
    FileInputStream fileInputStream = null;
    try {
      fileInputStream = new FileInputStream(file);
      SMHierarchicCursor cursor = inputFactory.rootElementCursor(new InputStreamReader(fileInputStream, getEncoding()));
      SMInputCursor mainCursor = cursor.advance().childElementCursor();
      while (mainCursor.getNext() != null) {
        if (NAMESPACES.equals(mainCursor.getQName().getLocalPart())) {
          parseNamespacesBloc(mainCursor);
        } else if (TARGETS.equals(mainCursor.getQName().getLocalPart())) {
          parseTargetsBloc(mainCursor);
        }
      }
      cursor.getStreamReader().closeCompletely();
    } catch (XMLStreamException e) {
      throw new SonarException("Error while reading FxCop result file: " + file.getAbsolutePath(), e);
    } catch (FileNotFoundException e) {
      throw new SonarException("Cannot find FxCop result file: " + file.getAbsolutePath(), e);
    } finally {
      IOUtils.closeQuietly(fileInputStream);
    }
  }

  private void parseNamespacesBloc(SMInputCursor cursor) throws XMLStreamException {
    // Cursor in on <Namespaces>
    SMInputCursor namespacesCursor = cursor.childElementCursor(NAMESPACE);
    while (namespacesCursor.getNext() != null) {
      SMInputCursor messagesCursor = namespacesCursor.descendantElementCursor(MESSAGE);
      while (messagesCursor.getNext() != null) {
        createViolationFromMessageAtProjectLevel(messagesCursor);
      }
    }
  }

  private void parseTargetsBloc(SMInputCursor cursor) throws XMLStreamException {
    // Cursor on <Targets>
    SMInputCursor modulesCursor = cursor.descendantElementCursor(MODULE);
    while (modulesCursor.getNext() != null) {
      parseModuleMessagesBloc(modulesCursor);
    }
  }

  private void parseModuleMessagesBloc(SMInputCursor cursor) throws XMLStreamException {
    // Cursor on <Module>
    SMInputCursor moduleChildrenCursor = cursor.childElementCursor();
    while (moduleChildrenCursor.getNext() != null) {
      if (MESSAGES.equals(moduleChildrenCursor.getQName().getLocalPart())) {
        // We are on <Messages>, look for <Message>
        SMInputCursor messagesCursor = moduleChildrenCursor.childElementCursor(MESSAGE);
        while (messagesCursor.getNext() != null) {
          createViolationFromMessageAtProjectLevel(messagesCursor);
        }
      } else if (NAMESPACES.equals(moduleChildrenCursor.getQName().getLocalPart())) {
        // We are on <Namespaces>, get <Namespace>
        SMInputCursor namespaceCursor = moduleChildrenCursor.childElementCursor();
        while (namespaceCursor.getNext() != null) {
          String namespaceName = namespaceCursor.getAttrValue(NAME);
          SMInputCursor typeCursor = namespaceCursor.childElementCursor().advance().childElementCursor();
          while (typeCursor.getNext() != null) {
            parseTypeBloc(namespaceName, typeCursor);
          }
        }
      }
    }
  }

  private void parseTypeBloc(String namespaceName, SMInputCursor cursor) throws XMLStreamException {
    // Cursor on <Type>
    String typeName = cursor.getAttrValue(NAME);
    Resource<?> typeResource = resourcesBridge.getFromTypeName(namespaceName, typeName);
    SMInputCursor messagesCursor = cursor.descendantElementCursor(MESSAGE);
    while (messagesCursor.getNext() != null) {
      // Cursor on <Message>
      if (messagesCursor.getCurrEvent() == SMEvent.START_ELEMENT) {

        Rule currentRule = ruleFinder.find(RuleQuery.create().withRepositoryKey(repositoryKey)
            .withKey(messagesCursor.getAttrValue(TYPENAME)));
        if (currentRule != null) {
          // look for all potential issues
          searchForViolations(messagesCursor, typeResource, currentRule);
        } else {
          LOG.warn("Could not find the following rule in the FxCop rule repository: " + messagesCursor.getAttrValue(TYPENAME));
        }

      }
    }
  }

  protected void searchForViolations(SMInputCursor messagesCursor, Resource<?> typeResource, Rule currentRule) throws XMLStreamException {
    SMInputCursor issueCursor = messagesCursor.childElementCursor();
    while (issueCursor.getNext() != null) {
      final Resource<?> resource;
      final boolean saveViolation;
      String path = issueCursor.getAttrValue("Path");
      String file = issueCursor.getAttrValue("File");
      if (StringUtils.isNotEmpty(path) && StringUtils.isNotEmpty(file)) {
        File sourceFile = new File(path, file).getAbsoluteFile();
        VisualStudioProject currentVsProject = vsSolution.getProject(sourceFile);
        if (vsProject.equals(currentVsProject)) {
          resource = org.sonar.api.resources.File.fromIOFile(sourceFile, project);
          saveViolation = true;
        } else {
          LOG.debug("Ignoring file outside current project : {}", sourceFile);
          resource = null;
          saveViolation = false;
        }
      } else if (typeResource == null || resourceHelper.isResourceInProject(typeResource, project)) {
        resource = typeResource;
        saveViolation = true;
      } else {
        resource = null;
        saveViolation = false;
      }

      if (saveViolation) {
        // Cursor on Issue
        Violation violation = Violation.create(currentRule, resource);
        String lineNumber = issueCursor.getAttrValue(LINE);
        if (lineNumber != null) {
          violation.setLineId(Integer.parseInt(lineNumber));
        }
        violation.setMessage(issueCursor.collectDescendantText().trim());
        violation.setSeverity(currentRule.getSeverity());
        context.saveViolation(violation);
      }
    }
  }

  private void createViolationFromMessageAtProjectLevel(SMInputCursor messagesCursor) throws XMLStreamException {
    Rule currentRule = ruleFinder.find(RuleQuery.create().withRepositoryKey(repositoryKey)
        .withKey(messagesCursor.getAttrValue(TYPENAME)));
    if (currentRule != null) {
      // the violation is saved at project level, not on a specific resource
      Violation violation = Violation.create(currentRule, project);
      violation.setMessage(messagesCursor.collectDescendantText().trim());
      violation.setSeverity(currentRule.getSeverity());
      context.saveViolation(violation);
    } else {
      LOG.debug("Could not find the following rule in the FxCop rule repository: " + messagesCursor.getAttrValue(TYPENAME));
    }
  }

}
