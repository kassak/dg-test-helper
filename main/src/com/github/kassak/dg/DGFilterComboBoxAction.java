package com.github.kassak.dg;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.RegExpFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DGFilterComboBoxAction extends ComboBoxAction implements DumbAware {
  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent c) {
    return new DefaultActionGroup();
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button, @NotNull DataContext dataContext) {
    Project project = dataContext.getData(PlatformDataKeys.PROJECT);
    if (project == null) return createPopupActionGroup(button);
    DGTestSettings settings = DGTestSettings.getInstance(project);
    List<AnAction> actions = new ArrayList<>();
    actions.add(new AnAction("New Filter...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        editFilter(project, ".*").thenAccept(res -> {
          DGTestSettings settings = DGTestSettings.getInstance(project);
          settings.getFilters().add(res);
          settings.setCurrent(res);
        });
      }
    });
    actions.add(new Separator());
    actions.add(new MyAskAction());
    actions.add(new MyOverwriteAction());
    actions.add(new MyInProcessRmiAction());
    actions.add(new MyAttachRemoteAction());
    actions.add(new Separator());
    for (String filter : settings.getFilters()) {
      actions.add(new MyFilterAction(filter));
    }

    return new DefaultActionGroup(actions) {
      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
  }

  @Override
  protected ComboBoxButton createComboBoxButton(Presentation presentation) {
    return new ComboBoxAction.ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(Runnable onDispose) {
        JBPopup popup = super.createPopup(onDispose);
        setUpPopup(popup);
        return popup;
      }
    };
  }

  private void setUpPopup(JBPopup popup) {
    if (popup instanceof ListPopupImpl) registerActions((ListPopupImpl) popup);
    popup.setAdText("Del - delete, F4 - edit, F3 - navigate", SwingConstants.LEFT);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    JFrame frame = WindowManager.getInstance().getFrame(project);
    if (!(frame instanceof IdeFrame)) return;

    ListPopup popup = createActionPopup(e.getDataContext(), ((IdeFrame)frame).getComponent(), null);
    setUpPopup(popup);
    popup.showCenteredInCurrentWindow(project);
  }

  @NotNull
  protected ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    DefaultActionGroup group = createPopupActionGroup(component, context);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, group, context, false, shouldShowDisabledActions(), false, disposeCallback, getMaxRows(), getPreselectCondition());
    popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return popup;
  }


  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    boolean isMenu = ActionPlaces.isMainMenuOrActionSearch(e.getPlace());
    boolean dg = project != null && isDGProject(project);
    presentation.setEnabledAndVisible(dg);
    String currentFilter = project == null ? null : DGTestSettings.getInstance(project).getCurrent();
    if (isMenu) {
      presentation.setText("Manage DS Filters...");
    }
    else {
      presentation.setText(StringUtil.notNullize(currentFilter, "<No DS filter>"), true);
    }
    presentation.setIcon(currentFilter == null ? AllIcons.General.Filter : ExecutionUtil.getLiveIndicator(AllIcons.General.Filter));
  }

  private void registerActions(@NotNull ListPopupImpl popup) {
    popup.registerAction("editFilter", KeyStroke.getKeyStroke("F4"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyFilterAction filter = getSelectedFilter(popup);
        if (filter == null) return;
        filter.edit(popup.getProject());
        popup.cancel();
      }
    });

    popup.registerAction("navigateFilter", KeyStroke.getKeyStroke("F3"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyFilterAction filter = getSelectedFilter(popup);
        if (filter == null) return;
        Pattern p;
        try {
          p = Pattern.compile(filter.myFilter);
        }
        catch (PatternSyntaxException pse) {
          return;
        }
        List<DGTestDataSources.DGTestDataSource> targets = DGTestDataSources.list(popup.getProject())
          .flatten(dss -> dss.dataSources)
          .filter(ds -> p.matcher(ds.uuid).matches())
          .toList();
        if (targets.isEmpty()) return;
        if (targets.size() == 1) {
          navigate(targets.get(0));
          return;
        }
        popup.cancel();
        JBPopupFactory.getInstance()
          .createPopupChooserBuilder(targets)
          .setItemChosenCallback(this::navigate)
          .setRenderer(SimpleListCellRenderer.create((lbl, o, i) -> {
            lbl.setIcon(o.getIcon());
            lbl.setText(o.uuid);
          }))
          .createPopup()
          .showInFocusCenter();
      }

      private void navigate(DGTestDataSources.DGTestDataSource ds) {
        XmlTag element = ds.source.getElement();
        DGFilterComboBoxAction.navigate(element, true);
      }
    });

    popup.registerAction("deleteFilter", KeyStroke.getKeyStroke("DELETE"),
      new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          MyFilterAction filter = getSelectedFilter(popup);
          if (filter == null) return;
          filter.delete(popup.getProject());
          popup.cancel();
        }
      });

  }

  public static void navigate(XmlTag element, boolean requestFocus) {
    if (element != null) {
      Navigatable descriptor = PsiNavigationSupport.getInstance().getDescriptor(element);
      if (descriptor != null) descriptor.navigate(requestFocus);
    }
  }

  @Nullable
  private MyFilterAction getSelectedFilter(@NotNull ListPopupImpl popup) {
    Object any = ArrayUtil.getFirstElement(popup.getSelectedValues());
    PopupFactoryImpl.ActionItem actionItem = ObjectUtils.tryCast(any, PopupFactoryImpl.ActionItem.class);
    AnAction action = actionItem == null ? null : actionItem.getAction();
    return ObjectUtils.tryCast(action, MyFilterAction.class);
  }

  private static class MyFilterAction extends ToggleAction implements DumbAware {
    private final String myFilter;

    public MyFilterAction(String filter) {
      super(StringUtil.escapeMnemonics(filter));
      this.myFilter = filter;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      String current = project == null ? null : DGTestSettings.getInstance(project).getCurrent();
      return myFilter.equals(current);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean selected) {
      Project project = e.getProject();
      if (project == null) return;
      DGTestSettings.getInstance(project).setCurrent(selected ? myFilter : null);
    }

    public void delete(@NotNull Project project) {
      DGTestSettings settings = DGTestSettings.getInstance(project);
      settings.getFilters().remove(myFilter);
      if (myFilter.equals(settings.getCurrent())) {
        settings.setCurrent(null);
      }
    }

    public void edit(@NotNull Project project) {
      editFilter(project, myFilter).thenAccept(res -> {
        DGTestSettings settings = DGTestSettings.getInstance(project);
        settings.getFilters().remove(myFilter);
        settings.getFilters().add(res);
        settings.setCurrent(res);
      });
    }
  }

  private static CompletionStage<String> editFilter(@NotNull Project project, @NotNull String def) {
    FormBuilder builder = FormBuilder.createFormBuilder();
    builder.getPanel().setBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));
    Ref<Consumer<KeyEvent>> closeOk = Ref.create();
    EditorTextField editor = new EditorTextField(def, project, RegExpFileType.INSTANCE) {
      @Override
      protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        if (!e.isConsumed() && e.getKeyCode() == KeyEvent.VK_ENTER && !closeOk.isNull()) {
          closeOk.get().consume(e);
          return true;
        }
        return super.processKeyBinding(ks, e, condition, pressed);
      }
    };
    editor.selectAll();
    ComponentWithBrowseButton<EditorTextField> comp = new ComponentWithBrowseButton<>(editor, e ->
      choosePredefined(project, editor, text -> {
        editor.setText(text);
        IdeFocusManager.getInstance(project).requestFocus(editor, true);
      }));
    builder.addLabeledComponent("Filter:", comp);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(builder.getPanel(), editor)
      .setTitle("Edit Filter")
      .setResizable(true)
      .setModalContext(true)
      .setFocusable(true)
      .setRequestFocus(true)
      .setMovable(true)
      .setBelongsToGlobalPopupStack(true)
      .setCancelKeyEnabled(true)
      .setCancelOnWindowDeactivation(false)
      .setCancelOnClickOutside(true)
      .addUserData("SIMPLE_WINDOW")
      .createPopup();
    closeOk.set(popup::closeOk);

    CompletableFuture<String> res = new CompletableFuture<>();
    popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        if (event.isOk()) {
          res.complete(editor.getText());
        }
        else {
          res.completeExceptionally(new ProcessCanceledException());
        }
      }
    });
    popup.setMinimumSize(new Dimension(200, 10));
    popup.showCenteredInCurrentWindow(project);
    return res;
  }

  private static void choosePredefined(@NotNull Project project, @NotNull JComponent e, @NotNull Consumer<String> s) {
    List<DGTestDataSources.DGTestDataSource> dss = DGTestDataSources.list(project).flatten(td -> td.dataSources).sort((ds1, ds2) -> StringUtil.naturalCompare(ds1.uuid, ds2.uuid)).toList();
    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<DGTestDataSources.DGTestDataSource>("Test Data Sources", dss) {
      @Override
      public Icon getIconFor(DGTestDataSources.DGTestDataSource value) {
        return value.getIcon();
      }

      @NotNull
      @Override
      public String getTextFor(DGTestDataSources.DGTestDataSource value) {
        return value.uuid;
      }

      @Nullable
      @Override
      public PopupStep<?> onChosen(DGTestDataSources.DGTestDataSource selectedValue, boolean finalChoice) {
        if (selectedValue != null) s.consume(selectedValue.uuid);
        return FINAL_CHOICE;
      }

      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }
    }).showUnderneathOf(e);
  }

  private static final Key<Boolean> IS_DG_PROJECT = Key.create("IS_DG_PROJECT");

  public static boolean isDGProject(@NotNull Project project) {
    Boolean isDG = IS_DG_PROJECT.get(project);
    if (isDG == null) {
      isDG = ModuleManager.getInstance(project).findModuleByName("intellij.database") != null;
      IS_DG_PROJECT.set(project, isDG);
    }
    return isDG;
  }

  private static class MyAskAction extends ToggleAction implements DumbAware/*KeepingPopupOpenAction*/ {
    public MyAskAction() {
      super("Always Ask");
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      return project != null && DGTestSettings.getInstance(project).isAsk();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean selected) {
      Project project = e.getProject();
      if (project != null) {
        DGTestSettings.getInstance(project).setAsk(selected);
      }
    }
  }

  private static class MyOverwriteAction extends ToggleAction implements DumbAware /*KeepingPopupOpenAction*/ {
    public MyOverwriteAction() {
      super("Overwrite Test Data");
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      return project != null && DGTestSettings.getInstance(project).isOverwrite();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean selected) {
      Project project = e.getProject();
      if (project != null) {
        DGTestSettings.getInstance(project).setOverwrite(selected);
      }
    }
  }

  private static class MyInProcessRmiAction extends ToggleAction implements DumbAware /*KeepingPopupOpenAction*/ {
    public MyInProcessRmiAction() {
      super("In-Process RMI");
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      return project != null && DGTestSettings.getInstance(project).isInProcessRmi();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean selected) {
      Project project = e.getProject();
      if (project != null) {
        DGTestSettings.getInstance(project).setInProcessRmi(selected);
      }
    }
  }

  private static class MyAttachRemoteAction extends ToggleAction implements DumbAware /*KeepingPopupOpenAction*/ {
    public MyAttachRemoteAction() {
      super("Attach Remote");
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      return project != null && DGTestSettings.getInstance(project).isAttachRemote();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean selected) {
      Project project = e.getProject();
      if (project != null) {
        DGTestSettings.getInstance(project).setAttachRemote(selected);
      }
    }
  }
}
