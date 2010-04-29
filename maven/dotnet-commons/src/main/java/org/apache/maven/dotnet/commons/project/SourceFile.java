/**
 * Maven and Sonar plugin for .Net
 * Copyright (C) 2010 Jose Chillan and Alexandre Victoor
 * mailto: jose.chillan@codehaus.org or alexvictoor@codehaus.org
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

/*
 * Created on Sep 1, 2009
 */
package org.apache.maven.dotnet.commons.project;

import java.io.File;

import org.codehaus.plexus.util.StringUtils;

/**
 * A source file included in a CSharp project.
 * 
 * @author Jose CHILLAN Sep 1, 2009
 */
public class SourceFile
{
  private final File                file;
  private final String              folder;
  private final String              name;
  private final VisualStudioProject project;

  /**
   * Constructs a @link{SourceFile}.
   * 
   * @param file the file canonical path
   * @param project the project that contains the file
   * @param folder the relative folder containing the file
   * @param fileName the file name
   */
  public SourceFile(VisualStudioProject project, File file, String folder, String fileName)
  {
    super();
    this.project = project;
    this.folder = folder;
    this.name = fileName;
    this.file = file;
  }

  /**
   * Returns the folder.
   * 
   * @return The folder to return.
   */
  public String getFolder()
  {
    return this.folder;
  }

  /**
   * Returns the fileName.
   * 
   * @return The fileName to return.
   */
  public String getName()
  {
    return this.name;
  }

  /**
   * Returns the project.
   * 
   * @return The project to return.
   */
  public VisualStudioProject getProject()
  {
    return this.project;
  }

  /**
   * @return
   */
  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("Source(");
    if (!StringUtils.isEmpty(folder))
    {
      builder.append(folder);
      builder.append("/");
    }
    builder.append(name);
    builder.append(")");
    return builder.toString();
  }

  
  /**
   * Returns the file.
   * 
   * @return The file to return.
   */
  public File getFile()
  {
    return this.file;
  }

}