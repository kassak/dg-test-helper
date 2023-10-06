package com.github.kassak.dg;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;

@State(name = "DGTestSettings", storages = {
  @Storage(StoragePathMacros.WORKSPACE_FILE),
})
public class DGTestSettings implements PersistentStateComponent<DGTestSettings.State> {
  @NotNull
  public static DGTestSettings getInstance(@NotNull Project project) {
    return project.getService(DGTestSettings.class);
  }

  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @NotNull
  public Set<String> getFilters() {
    return myState.filters;
  }

  @Nullable
  public String getCurrent() {
    return myState.current;
  }

  public void setCurrent(@Nullable String current) {
    myState.current = current;
  }

  public boolean isAsk() {
    return myState.ask;
  }

  public void setAsk(boolean ask) {
    myState.ask = ask;
  }

  public boolean isOverwrite() {
    return myState.overwrite;
  }

  public void setOverwrite(boolean overwrite) {
    myState.overwrite = overwrite;
  }

  public boolean isInProcessRmi() {
    return myState.inProcessRmi;
  }

  public void setInProcessRmi(boolean inProcessRmi) {
    myState.inProcessRmi = inProcessRmi;
  }

  public boolean isAttachRemote() {
    return myState.attachRemote;
  }

  public void setAttachRemote(boolean attachRemote) {
    myState.attachRemote = attachRemote;
  }


  public static class State {
    public Set<String> filters = new TreeSet<>();
    public String current;
    public boolean ask;
    public boolean overwrite;
    public boolean inProcessRmi;
    public boolean attachRemote;
  }
}
