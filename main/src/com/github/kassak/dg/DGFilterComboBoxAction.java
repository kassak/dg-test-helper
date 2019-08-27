package com.github.kassak.dg;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.popup.KeepingPopupOpenAction;
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
    actions.add(new MyAskAction());
    for (String filter : settings.getFilters()) {
      actions.add(new MyFilterAction(filter));
    }

    return new DefaultActionGroup(actions);
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
    popup.setAdText("Del - delete, F4 - edit", SwingConstants.LEFT);
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
  private ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
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
    boolean isMenu = ActionPlaces.isMainMenuOrShortcut(e.getPlace());
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

  @Nullable
  private MyFilterAction getSelectedFilter(@NotNull ListPopupImpl popup) {
    Object any = ArrayUtil.getFirstElement(popup.getSelectedValues());
    PopupFactoryImpl.ActionItem actionItem = ObjectUtils.tryCast(any, PopupFactoryImpl.ActionItem.class);
    AnAction action = actionItem == null ? null : actionItem.getAction();
    return ObjectUtils.tryCast(action, MyFilterAction.class);
  }

  private static class MyFilterAction extends ToggleAction {
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
    builder.addLabeledComponent("Filter:", editor);
    JBPopup popup = NewItemPopupUtil.createNewItemPopup("Edit Filter", builder.getPanel(), editor);
    closeOk.set(popup::closeOk);

    CompletableFuture<String> res = new CompletableFuture<>();
    popup.addListener(new JBPopupAdapter() {
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

  private static final Key<Boolean> IS_DG_PROJECT = Key.create("IS_DG_PROJECT");

  private static boolean isDGProject(@NotNull Project project) {
    Boolean isDG = IS_DG_PROJECT.get(project);
    if (isDG == null) {
      isDG = ModuleManager.getInstance(project).findModuleByName("intellij.database") != null;
      IS_DG_PROJECT.set(project, isDG);
    }
    return isDG;
  }

  private static class MyAskAction extends ToggleAction/* implements KeepingPopupOpenAction*/ {
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
}
