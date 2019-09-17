package com.github.kassak.dg;

import com.intellij.database.Dbms;

import javax.swing.*;

public class DGPresentationHelper implements DGTestDataSources.PresentationHelper {
  @Override
  public Icon getIcon(String dbmsName) {
    Dbms dbms = Dbms.byName(dbmsName);
    return dbms == null ? null : dbms.getIcon();
  }

  @Override
  public Icon detectIcon(String text) {
    Dbms dbms = Dbms.fromString(text);
    return dbms == Dbms.UNKNOWN ? null : dbms.getIcon();
  }
}
