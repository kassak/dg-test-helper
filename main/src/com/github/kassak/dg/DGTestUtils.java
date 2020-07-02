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
  public static JBIterable<ContentEntry> getContent(@NotNull Project project, String moduleName) {
    Module tests = DGFilterComboBoxAction.isDGProject(project) ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    return JBIterable.of(tests == null ? null : ModuleRootManager.getInstance(tests).getContentEntries());
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
