package com.github.kassak.dg;

import com.github.kassak.dg.DGTestUtils.ConfigFile;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.github.kassak.dg.DGTestUtils.ConfigItem;

public class DGTestConfigEntityContributor implements GotoClassContributor, ChooseByNameContributorEx {

  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    Project project = scope.getProject();
    if (project == null) return;
    getConfigs(project)
      .flatten(ConfigFile::getItems)
      .map(ConfigItem::getName)
      .unique()
      .processEach(processor);
  }

  @NotNull
  private JBIterable<ConfigFile<?>> getConfigs(Project project) {
    return JBIterable.<ConfigFile<?>>from(DGTestDataSources.list(project))
      .append(DGTestDrivers.list(project))
      .append(DGTestArtifacts.list(project));
  }

  @Override
  public void processElementsWithName(@NotNull String s, @NotNull Processor<? super NavigationItem> processor, @NotNull FindSymbolParameters findSymbolParameters) {
    Project project = findSymbolParameters.getProject();
    for (ConfigFile<?> file : getConfigs(project)) {
      for (ConfigItem item : file.getItems()) {
        if (s.equals(item.getName())) {
          processor.process(asNavigationItem(item, file.getFileName()));
        }
      }
    }
  }

  @NotNull
  private NavigationItem asNavigationItem(ConfigItem item, String fileName) {
    return new NavigationItem() {
      @Override
      public String getName() {
        return item.getName();
      }

      @Override
      public ItemPresentation getPresentation() {
        return new ItemPresentation() {
          @Override
          public String getPresentableText() {
            return item.getName();
          }

          @Nullable
          @Override
          public String getLocationString() {
            return fileName;
          }

          @Nullable
          @Override
          public Icon getIcon(boolean unused) {
            return item.getIcon();
          }
        };
      }

      @Override
      public void navigate(boolean requestFocus) {
        DGFilterComboBoxAction.navigate(item.getSource(), requestFocus);
      }

      @Override
      public boolean canNavigate() {
        return true;
      }

      @Override
      public boolean canNavigateToSource() {
        return true;
      }
    };
  }

  @Nullable
  @Override
  public String getQualifiedName(@NotNull NavigationItem navigationItem) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedNameSeparator() {
    return null;
  }
}
