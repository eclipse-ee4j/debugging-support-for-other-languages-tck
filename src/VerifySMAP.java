/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

import java.util.*;    
import java.io.*; 

class VerifySMAP {

  private static final int INIT_SIZE_FILE = 3;
  private static final int INIT_SIZE_LINE = 100;
  private static final int INIT_SIZE_STRATUM = 3;
  
  static final String BASE_STRATUM_NAME = "Java";
  
  private class FileTableRecord {
    int fileId;
    String sourceName;
    String sourcePath; 
  }
  
  private class LineTableRecord {
    int jplsStart;
    int jplsEnd;
    int jplsLineInc;
    int njplsStart;
    int njplsEnd;
    int fileId;
  }
  
  private class StratumTableRecord {
    String id;
    int fileIndex;
    int lineIndex;
    int fileSectionCount;
    int lineSectionCount;
  }
        
  static void configError(String msg) {
    System.err.println(source + ": Configuration error - " + msg);
    throw new RuntimeException(msg);
  }

  static byte[] readWhole(String pn) throws IOException {
    File inFile = new File(pn);
    if (!inFile.exists()) {
      configError("File does not exists");
    }
    long length = inFile.length();
    if (length > Integer.MAX_VALUE) {
      configError("File too big to verify");
    }
    int len = (int)length;
    FileInputStream inStream = new FileInputStream(inFile);
    byte[] bytes = new byte[len];
    if (inStream.read(bytes, 0, len) != len) {
      configError("unexpected size of read file, expected size: " + len);
    }
    inStream.close();
    return bytes;
  }

  static class ExtractSDE {
    static final String nameSDE = "SourceDebugExtension";

    final byte[] classFile;
    int classFilePos = 0;

    ExtractSDE(String inName) throws IOException {
      // get the bytes
      classFile = readWhole(inName);
    }

    String parseClassFile() {
      long magic = readU4();
      if (magic != 0xCAFEBABEL) {
        configError("class file not in Java Virtual Machine class file format - bad value of magic: 0x" + 
                    Long.toHexString(magic));
      }
      skip(2 + 2); // min/maj version
      int cpIndexSDE = readConstantPool();
      if (cpIndexSDE == 0) {
        configError("No Source Debug Extension in this class file (attribute name not defined)");
      }
      skip(2 + 2 + 2);  // access, this, super
      int interfaceCount = readU2();
      skip(interfaceCount * 2);
      skipMembers(); // fields
      skipMembers(); // methods
      int attrCount = readU2();
      for (int i = 0; i < attrCount; ++i) {
        String sde = readAttr(cpIndexSDE);
        if (sde != null) {
          // we found the SDE
          return sde;
        }
      }
      configError("No Source Debug Extension in this class file");
      return null; // not reached
    }

    void skipMembers() {
      int count = readU2();
      for (int i = 0; i < count; ++i) {
        skip(6); // access, name, descriptor
        int attrCount = readU2();
        for (int j = 0; j < attrCount; ++j) {
          skipAttr();
        }
      }
    }

    void skipAttr() {
      skip(2);  // name
      int len = (int)readU4();
      skip(len);
    }

    String readAttr(int cpIndexSDE) {
      int name = readU2();
      int len = (int)readU4();
      if (name == cpIndexSDE) {
        // this is the SDE attribute - return the value
        return read(len);
      } else {
        // this is some attribute we don't care about
        skip(len);
        return null;
      }
    }

    int readU1() {
      return ((int)classFile[classFilePos++]) & 0xFF;
    }

    int readU2() {
      int res = readU1();
      return (res << 8) + readU1();
    }
    
    long readU4() {
      long res = readU2();
      return (res << 16) + readU2();
    }
    
    String read(int count) {
      byte[] result = new byte[count];
      for (int i = 0; i < count; ++i) {
        result[i] = classFile[classFilePos++];
      }
      return new String(result);
    }
    
    void skip(int count) {
      classFilePos += count;
    }
    
    // read the constant pool and return the index of the SDE utf8
    int readConstantPool() {
      int cpIndexSDE =  0;
      int constantPoolCount = readU2();
      // read const pool - look for SDE
      // note: index zero is not in class file
      for (int i = 1; i < constantPoolCount; ++i) {
        int tag = readU1();
        switch (tag) {
        case 7:  // Class
        case 8:  // String
          skip(2); 
          break;
        case 9:  // Field
        case 10: // Method
        case 11: // InterfaceMethod
        case 3:  // Integer
        case 4:  // Float
        case 12: // NameAndType
          skip(4); 
          break;
        case 5:  // Long
        case 6:  // Double
          skip(8); 
          ++i; // see VM Spec 2nd ed. 4.4.5
          break;
        case 1:  // Utf8
          int len = readU2(); 
          String utf8 = read(len);
          if (utf8.equals(nameSDE)) {
            cpIndexSDE = i;
          }
          break;
        default: 
          configError("bad class file - unexpected constant pool tag: " + tag); 
          break;
        }
      }
      return cpIndexSDE;
    }
  }

  class AssertionViolationException extends RuntimeException {    
    final int assertionNumber;
    final String message;

    AssertionViolationException(int assertionNumber, String message) {
      super("assertion #" + assertionNumber + " failed - " + message);
      this.assertionNumber = assertionNumber;
      this.message = message;
    }
  }
        
  private FileTableRecord[] fileTable = null;
  private LineTableRecord[] lineTable = null;
  private StratumTableRecord[] stratumTable = null;
  
  private int fileIndex = 0;
  private int lineIndex = 0;
  private int stratumIndex = 0;
  private int currentFileId = 0;

  private int defaultStratumIndex = -1;
  private String jplsFilename = null; 
  private String defaultStratumId = null;
  private boolean parseComplete = false;
  
  private static int sdePos = 0;
  private static int lineNumber = 1;

  private static String smap;
  private static String source;
  private static boolean resolved;
  
  VerifySMAP(String smap0, boolean resolved0) {
    smap = smap0;
    resolved = resolved0;
  }

  // For embedded SMAPs
  VerifySMAP() {
  }

  char sdePeek() {
    if (sdePos >= smap.length()) {
      syntax("unexpected end of SMAP");
    }
    return smap.charAt(sdePos);
  }

  char sdeRead() {
    if (sdePos >= smap.length()) {
      syntax("unexpected end of SMAP");
    }
    return smap.charAt(sdePos++);
  }

  void sdeAdvance() {
    sdePos++;
  }

  void error(int assertionNumber, String msg, String detail) {
    System.err.println("Assertion #" + assertionNumber + " failed - " + msg);
    System.err.println(source + (parseComplete? "" : ":" + lineNumber) + ": " + detail);
    throw new AssertionViolationException(assertionNumber, msg);
  }

  void error(int assertionNumber, String msg) {
    System.err.println(source + (parseComplete? "" : ":" + lineNumber) + ": Assertion #" + assertionNumber + " failed - " + msg);
    throw new AssertionViolationException(assertionNumber, msg);
  }

  void syntax(String msg) {
    error(1, "SMAP syntax error", msg);
  }

  void assureLineTableSize() {
    int len = lineTable == null? 0 : lineTable.length;
    if (lineIndex >= len) {
      int i;
      int newLen = len == 0? INIT_SIZE_LINE : len * 2;
      LineTableRecord[] newTable = new LineTableRecord[newLen];
      for (i = 0; i < len; ++i) {
        newTable[i] = lineTable[i];
      }
      for (; i < newLen; ++i) {
        newTable[i] = new LineTableRecord();
      }
      lineTable = newTable;
    }
  }

  void assureFileTableSize() {
    int len = fileTable == null? 0 : fileTable.length;
    if (fileIndex >= len) {
      int i;
      int newLen = len == 0? INIT_SIZE_FILE : len * 2;
      FileTableRecord[] newTable = new FileTableRecord[newLen];
      for (i = 0; i < len; ++i) {
        newTable[i] = fileTable[i];
      }
      for (; i < newLen; ++i) {
        newTable[i] = new FileTableRecord();
      }
      fileTable = newTable;
    }
  }

  void assureStratumTableSize() {
    int len = stratumTable == null? 0 : stratumTable.length;
    if (stratumIndex >= len) {
      int i;
      int newLen = len == 0? INIT_SIZE_STRATUM : len * 2;
      StratumTableRecord[] newTable = new StratumTableRecord[newLen];
      for (i = 0; i < len; ++i) {
        newTable[i] = stratumTable[i];
      }
      for (; i < newLen; ++i) {
        newTable[i] = new StratumTableRecord();
      }
      stratumTable = newTable;
    }
  }

  String readLine() {
    StringBuffer sb = new StringBuffer();
    char ch;

    ignoreWhite();
    while (((ch = sdeRead()) != '\n') && (ch != '\r')) {
      sb.append((char)ch);
    }
    // check for CR LF
    if ((ch == '\r') && (sdePeek() == '\n')) {
      sdeRead();
    }
    ignoreWhite(); // leading white

    ++lineNumber;
    return sb.toString().trim();
  }

  private int defaultStratumTableIndex() {
    if ((defaultStratumIndex == -1) && (defaultStratumId != null)) {
      defaultStratumIndex = 
        stratumTableIndex(defaultStratumId);
    }
    return defaultStratumIndex;
  }   

  int stratumTableIndex(String stratumId) {
    int i;

    if (stratumId == null) {
      return defaultStratumTableIndex();
    }
    for (i = 0; i < (stratumIndex-1); ++i) {
      if (stratumTable[i].id.equals(stratumId)) {
        return i;
      }
    }
    return defaultStratumTableIndex();
  }   

  void ignoreWhite() {
    char ch;

    while (((ch = sdePeek()) == ' ') || (ch == '\t')) {
      sdeAdvance();
    }
  }

  void skipRemainingWhite() {
    char ch;

    ignoreWhite(); /* trailing white */
    ch = sdeRead();
    if ((ch != '\n') && (ch != '\r')) {
      syntax("unexpected characters at end of line: " + ch + readLine());
    }
    /* check for CR LF */
    if ((ch == '\r') && (sdePeek() == '\n')) {
      sdeAdvance();
    }
    ignoreWhite(); /* leading white */
    ++lineNumber;
  }

  int readNumber() {
    int value = 0;
    char ch;

    ignoreWhite();
    if (((ch = sdePeek()) < '0') || (ch > '9')) {
      syntax("Number expected");
    }
    while (((ch = sdePeek()) >= '0') && (ch <= '9')) {
      sdeAdvance();
      value = (value * 10) + ch - '0';
    }
    ignoreWhite();
    return value;
  }

  void storeFile(int fileId, String sourceName, String sourcePath) {
    assureFileTableSize();
    fileTable[fileIndex].fileId = fileId;
    fileTable[fileIndex].sourceName = sourceName;
    fileTable[fileIndex].sourcePath = sourcePath;
    ++fileIndex;
  }

  void fileLine() {
    int hasAbsolute = 0; /* acts as boolean */
    int fileId;
    String sourceName;
    String sourcePath = null;

    /* is there an absolute filename? */
    if (sdePeek() == '+') {
      sdeAdvance();
      hasAbsolute = 1;
    }
    fileId = readNumber();
    if (fileTableIndex(stratumIndex-1, fileId) != -1) {
      error(7, "In a FileSection, each FileId must be unique within that FileSection", "FileId = " + fileId);
    }
    sourceName = readLine();
    if (sourceName.length() == 0) {
      error(8, "In a FileSection, the FileName must be non empty");
    }
    if (hasAbsolute == 1) {
      sourcePath = readLine();
      if (sourcePath.length() == 0) {
        error(9, "In a FileSection, the AbsoluteFileName, if specified, must be non empty");
      }
    }

    storeFile(fileId, sourceName, sourcePath);
  }

  void storeLine(int jplsStart, int jplsEnd, int jplsLineInc, 
                 int njplsStart, int njplsEnd, int fileId) {
    assureLineTableSize();
    lineTable[lineIndex].jplsStart = jplsStart;
    lineTable[lineIndex].jplsEnd = jplsEnd;
    lineTable[lineIndex].jplsLineInc = jplsLineInc;
    lineTable[lineIndex].njplsStart = njplsStart;
    lineTable[lineIndex].njplsEnd = njplsEnd;
    lineTable[lineIndex].fileId = fileId;
    ++lineIndex;
  }

  /**
   * Parse line translation info.  Syntax is
   *     <NJ-start-line> [ # <file-id> ] [ , <line-count> ] : 
   *                 <J-start-line> [ , <line-increment> ] CR
   */
  void lineLine() {
    int lineCount = 1;
    int lineIncrement = 1;
    int njplsStart;
    int jplsStart;

    njplsStart = readNumber();
    if (njplsStart < 1) {
      error(14, "In a LineSection, InputStartLine must be greater than or equal to one", "InputStartLine = " + njplsStart);
    }

    /* is there a fileID? */
    if (sdePeek() == '#') {
      sdeAdvance();
      currentFileId = readNumber();
    }

    /* is there a line count? */
    if (sdePeek() == ',') {
      sdeAdvance();
      lineCount = readNumber();
      if (lineCount < 1) {
        error(12, "In a LineSection, RepeatCount must be greater than or equal to one", "RepeatCount = " + lineCount);
      }
    }

    if (sdeRead() != ':') {
      syntax("expected a ':'");
    }
    jplsStart = readNumber();
    if (jplsStart < 1) {
      error(15, "In a LineSection,  OutputStartLine  must be greater than or equal to one", "OutputStartLine = " + jplsStart);
    }
    if (sdePeek() == ',') {
      sdeAdvance();
      lineIncrement = readNumber();
    }
    skipRemainingWhite(); /* flush the rest */
        
    storeLine(jplsStart,
              jplsStart + (lineCount * lineIncrement) -1,
              lineIncrement,
              njplsStart,
              njplsStart + lineCount -1,
              currentFileId);
  }

  /**
   * Until the next stratum section, everything after this
   * is in stratumId - so, store the current indicies.
   */
  void storeStratum(String stratumId) {
    /* store the results */
    assureStratumTableSize();
    stratumTable[stratumIndex].id = stratumId;
    stratumTable[stratumIndex].fileIndex = fileIndex;
    stratumTable[stratumIndex].lineIndex = lineIndex;
    stratumTable[stratumIndex].fileSectionCount = 0;
    stratumTable[stratumIndex].lineSectionCount = 0;
    ++stratumIndex;
    currentFileId = 0;
  }

  /**
   * The beginning of a stratum's info
   */
  void stratumSection() {
    String stratumId = readLine();
    if (stratumId.equals(BASE_STRATUM_NAME)) {
      error(4, "'Java' stratum cannot be defined");
    }
    storeStratum(stratumId);
  }

  void fileSection() {
    skipRemainingWhite();
    if (stratumIndex == 0) {
      error(5, "FileSection must only occur after StratumSection");
    } else {
      stratumTable[stratumIndex-1].fileSectionCount++;
    }
    while (sdePeek() != '*') {
      fileLine();
    }
  }

  void lineSection() {
    skipRemainingWhite();
    if (stratumIndex == 0) {
      error(10, "LineSection must only occur after StratumSection");
    } else {
      stratumTable[stratumIndex-1].lineSectionCount++;
    }
    while (sdePeek() != '*') {
      lineLine();
    }
  }

  void vendorSection() {
    skipRemainingWhite();
    String vendorId = readLine();
    if (vendorId.length() == 0) {
      error(17, "VENDORID missing");
    }
    for (int i = 0; i < vendorId.length(); ++i) {
      char ch = vendorId.charAt(i);
      if (ch != '.' && !Character.isJavaIdentifierPart(ch)) {
        error(17, "VENDORID contains invalid character", "Bad character: '" + ch + "' at position " + i);
      }
    }
    ignoreSection();
  }

  void openEmbeddedSection() {
    if (resolved) {
      error(20, "An embedded SMAP must not occur in a resolved SMAP");
    }
    String outputStratumId = readLine();
    if (outputStratumId.length() == 0) {
      syntax("Output StratumID missing");
    }
    do {
      // recurse
      VerifySMAP vsmap = new VerifySMAP();
      if (vsmap.decode()) {
        vsmap.verify();
      }
      skipRemainingWhite();  // Get past the '*E'
    } while (sdePeek() != '*');
    if (sdeRead() != '*') {
      syntax("expected a '*'");
    }
    char sectionType = sdeRead();
    if (sectionType != 'C') {
      error(21, "CloseEmbeddedSection must terminate an OpenEmbeddedSection", "expected a CloseEmbeddedSection - '*C'");
    }
    String closeStratumId = readLine();
    if (!closeStratumId.equals(outputStratumId)) {
      error(22, "StratumId of CloseEmbeddedSection does not match StratumId of OpenEmbeddedSection",
            "'" + closeStratumId + "' vs. '" + outputStratumId + "'");
    }
  }

  /**
   * Ignore a section we don't know about.
   */
  void ignoreSection() {
    while (sdePeek() != '*') {
      readLine();
    }
  }

  /**
   * Decode an SMAP.
   * This is the entry point into the recursive descent parser.
   */
  boolean decode() {
    /* check for "SMAP" */
    if ((smap.length() < 4) ||
        (sdeRead() != 'S') ||
        (sdeRead() != 'M') ||
        (sdeRead() != 'A') ||
        (sdeRead() != 'P')) {
      syntax("Not an SMAP - does not start with 'SMAP'"); /* not our info */
    }
    skipRemainingWhite(); /* flush the rest */
    jplsFilename = readLine();
    defaultStratumId = readLine();
    while (true) {
      if (sdeRead() != '*') {
        syntax("expected a '*'");
      }
      char sectionType = sdeRead();
      switch (sectionType) {
      case 'S': 
        stratumSection();
        break;
      case 'F': 
        fileSection();
        break;
      case 'L': 
        lineSection();
        break;
      case 'V': 
        vendorSection();
        break;
      case 'E': 
        /* set end points */
        storeStratum("*terminator*"); 
        parseComplete = true;  // mark not location specific
        return true;
      case 'O':
        openEmbeddedSection();
        break;
      default:
        error(18, "Unknown section type", "Section type: *" + sectionType);
        ignoreSection();
      }
    }
  }

  void verify() {
    if (stratumIndex == 0) {
      error(19, "There must be at least one StratumSection");
    }
    if (defaultStratumId.length() == 0) {
      if (resolved) {
        error(2, "resolved SMAP with unspecified DefaultStratumId");
      } else {
        // unresolved SMAPs need not specify the default stratum
      }
    } else if (!defaultStratumId.equals(BASE_STRATUM_NAME)) {
      boolean found = false;
      for (int sti = 0; sti < stratumIndex; ++sti) {
        if (stratumTable[sti].id.equals(defaultStratumId)) {
          found = true;
          break;
        }
      }
      if (!found) {
        error(3, "DefaultStratumId is not a valid stratum", "DefaultStratumId = '" + defaultStratumId + "'");
      }
    }
    for (int sti = 0; sti < (stratumIndex-1); ++sti) {
      if (stratumTable[sti].fileSectionCount != 1) {
        error(6, "There must be exactly one FileSection after each StratumSection",
              "There are " + stratumTable[sti].fileSectionCount + " file sections");
      }
    }
    for (int sti = 0; sti < (stratumIndex-1); ++sti) {
      if (stratumTable[sti].lineSectionCount != 1) {
        error(11, "There must be exactly one LineSection after each StratumSection",
              "There are " + stratumTable[sti].lineSectionCount + " line sections");
      }
    }
    for (int sti = 0; sti < (stratumIndex-1); ++sti) {
      int lineIndexStart = stratumTable[sti].lineIndex;
      int lineIndexEnd = stratumTable[sti+1].lineIndex;   /* one past end */
      for (int li = lineIndexStart; li < lineIndexEnd; ++li) {
        if (fileTableIndex(sti, lineTable[li].fileId) == -1) {
          error(16, "In a LineSection, LineFileId must be a FileId in the FileSection after the same StratumSection",
                "FileId " + lineTable[li].fileId + " not found");
        }
      }
    }
  }


  /***************** query functions ***********************/

  private int stiLineTableIndex(int sti, int jplsLine) {
    int i;
    int lineIndexStart;
    int lineIndexEnd;

    lineIndexStart = stratumTable[sti].lineIndex;
    /* one past end */
    lineIndexEnd = stratumTable[sti+1].lineIndex; 
    for (i = lineIndexStart; i < lineIndexEnd; ++i) {
      if ((jplsLine >= lineTable[i].jplsStart) &&
          (jplsLine <= lineTable[i].jplsEnd)) {
        return i;
      }
    }
    return -1;
  }

  private int stiLineNumber(int sti, int lti, int jplsLine) {
    return lineTable[lti].njplsStart + 
      (((jplsLine - lineTable[lti].jplsStart) / 
        lineTable[lti].jplsLineInc));
  }

  private int fileTableIndex(int sti, int fileId) {
    int i;
    int fileIndexStart = stratumTable[sti].fileIndex;
    /* one past end */
    int fileIndexEnd = stratumTable[sti+1].fileIndex; 
    for (i = fileIndexStart; i < fileIndexEnd; ++i) {
      if (fileTable[i].fileId == fileId) {
        return i;
      }
    }
    return -1;
  }

  private int stiFileTableIndex(int sti, int lti) {
    return fileTableIndex(sti, lineTable[lti].fileId);
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      configError("Must be exactly one argument (the SMAP or class file to verify)");
    }
    System.out.println();
    source = args[0];
    String smap;
    boolean resolved = false;
    if (source.endsWith(".class")) {
      smap = (new ExtractSDE(source)).parseClassFile();
      resolved = true;
    } else {
      smap = new String(readWhole(source));
    }
    VerifySMAP vsmap = new VerifySMAP(smap, resolved);
    if (vsmap.decode()) {
      vsmap.verify();
    }
    System.out.println(source + (resolved? " contains" : " is") + " a correctly formatted SMAP");
  }
}    
