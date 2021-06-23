package com.github.kassak.dg;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DGTestUtils {
  @NotNull
  public static JBIterable<ContentEntry> getTestContents(@NotNull Project project) {
    return DGTestUtils.getContent(project, "intellij.database.tests")
      .append(DGTestUtils.getContent(project, "intellij.database.connectivity.tests"));
  }

  @NotNull
  public static JBIterable<ContentEntry> getContent(@NotNull Project project, String moduleName) {
    return getModuleContent(findDGModule(project, moduleName));
  }

  @NotNull
  public static JBIterable<ContentEntry> getModuleContent(@Nullable Module m) {
    return JBIterable.of(m == null ? null : ModuleRootManager.getInstance(m).getContentEntries());
  }

  @Nullable
  public static Module findDGModule(@NotNull Project project, String moduleName) {
    return DGFilterComboBoxAction.isDGProject(project) ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
  }

  @NotNull
  public static JBIterable<Module> getDGModules(@NotNull Project project) {
    return DGFilterComboBoxAction.isDGProject(project)
      ? JBIterable.of(ModuleManager.getInstance(project).getModules())
      : JBIterable.empty();
  }
  @NotNull
  public static JBIterable<Module> findDGSubModules(@NotNull Project project, String moduleNamePrefix) {
    return getDGModules(project).filter(m -> m.getName().startsWith(moduleNamePrefix));
  }

  @NotNull
  public static NullableLazyValue<Icon> createDbmsCorneredIcon(String dbmsStr, Icon corner) {
    return NullableLazyValue.createValue(() -> {
      PresentationHelper ph = ServiceManager.getService(PresentationHelper.class);
      if (ph == null) return null;
      Icon dbmsIcon = ObjectUtils.notNull(ph.detectIcon(dbmsStr), EmptyIcon.ICON_16);
      LayeredIcon result = new LayeredIcon(2);
      result.setIcon(dbmsIcon, 0);
      int h = dbmsIcon.getIconHeight() / 2;
      int w = dbmsIcon.getIconWidth() / 2;
      result.setIcon(IconUtil.toSize(corner, w, h), 1, w, h);
      return result;
    });
  }

  public interface ConfigFile<T extends ConfigItem> {
    @NotNull
    String getFileName();

    @NotNull
    JBIterable<T> getItems();
  }

  public interface ConfigItem {
    @NotNull
    String getName();

    @Nullable
    Icon getIcon();

    @Nullable
    XmlTag getSource();

  }

  public interface PresentationHelper {
    Icon getIcon(String dbms);
    Icon detectIcon(String text);
  }
}
