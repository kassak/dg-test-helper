package com.github.kassak.dg;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DGConfigurationExtension extends RunConfigurationExtension {

  private static final String DB_FILTER = "db.filter";
  private static final String OVERWRITE_DATA = "idea.tests.overwrite.data";
  private static final String IN_PROCESS_RMI = "idea.rmi.server.in.process";

  @Override
  public <T extends RunConfigurationBase> void updateJavaParameters(@NotNull T configuration, @NotNull JavaParameters parameters, RunnerSettings settings) throws ExecutionException {
    if (!isApplicableFor(configuration)) return;
    Project project = configuration.getProject();
    ParametersList params = parameters.getVMParametersList();
    if (!params.hasProperty(DB_FILTER)) {
      String filter = getFilter(project);
      params.defineProperty(DB_FILTER, filter);
    }
    if (!params.hasProperty(OVERWRITE_DATA) && DGTestSettings.getInstance(project).isOverwrite()) {
      params.defineProperty(OVERWRITE_DATA, "true");
    }
    if (!params.hasProperty(IN_PROCESS_RMI) && DGTestSettings.getInstance(project).isInProcessRmi()) {
      params.defineProperty(IN_PROCESS_RMI, "true");
    }
  }

  private String getFilter(Project project) {
    DGTestSettings settings = DGTestSettings.getInstance(project);
    if (!settings.isAsk()) {
      return settings.getCurrent();
    }
    String noFilter = "<No filter>";
    String manage = "<Manage...>";
    List<String> variants = new ArrayList<>();
    variants.add(noFilter);
    variants.addAll(settings.getFilters());
    variants.add(manage);
    Ref<String> res = Ref.create();
    SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
    JBPopupFactory.getInstance().createPopupChooserBuilder(variants)
      .setSelectedValue(settings.getCurrent(), true)
      .setItemChosenCallback(res::set)
      .addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          loop.exit();
        }
      })
      .setRenderer(new ColoredListCellRenderer<String>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends String> list, String o, int i, boolean b, boolean b1) {
          append(StringUtil.notNullize(o), noFilter.equals(o) || manage.equals(o) ? SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      })
      .createPopup()
      .showCenteredInCurrentWindow(project);
    loop.enter();
    if (res.isNull()) {
      throw new ProcessCanceledException();
    }
    if (manage.equals(res.get())) {
      ApplicationManager.getApplication().invokeLater(() -> {
        AnAction action = ActionManager.getInstance().getAction("DGManageFilters");
        if (action == null) {
          throw new AssertionError("No manage action");
        }
        JFrame frame = WindowManager.getInstance().getFrame(project);
        ActionUtil.invokeAction(action, DataManager.getInstance().getDataContext(frame), ActionPlaces.KEYBOARD_SHORTCUT, null, null);
      });
      throw new ProcessCanceledException();
    }
    return noFilter.equals(res.get()) ? null : res.get();
  }

  @Override
  public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
    JavaTestConfigurationBase javaConfig = ObjectUtils.tryCast(configuration, JavaTestConfigurationBase.class);
    if (javaConfig == null) return false;
    return Arrays.stream(javaConfig.getModules())
      .anyMatch(m -> m.getName().startsWith("intellij.database"));
  }
}
