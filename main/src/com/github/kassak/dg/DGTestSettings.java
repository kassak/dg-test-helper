package com.github.kassak.dg;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

@State(name = "DGTestSettings", storages = {
  @Storage(StoragePathMacros.WORKSPACE_FILE),
})
public class DGTestSettings implements PersistentStateComponent<DGTestSettings.State> {
  @NotNull
  public static DGTestSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DGTestSettings.class);
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


  public static class State {
    public Set<String> filters = ContainerUtil.newTreeSet();
    public String current;
    public boolean ask;
  }
}
