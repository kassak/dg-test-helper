package com.github.kassak.dg;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DGTestDrivers implements DGTestUtils.ConfigFile<DGTestDrivers.DGTestDriver> {
  public final String fileName;
  public final List<DGTestDriver> drivers;

  public DGTestDrivers(String fileName, List<DGTestDriver> drivers) {
    this.fileName = fileName;
    this.drivers = drivers;
  }

  @NotNull
  public static JBIterable<DGTestDrivers> list(@NotNull Project project) {
    JBIterable<VirtualFile> td = DGTestUtils.getContent(project, "intellij.database.tests")
      .flatten(e -> e.getSourceFolders(JavaResourceRootType.TEST_RESOURCE))
      .filterMap(ContentFolder::getFile)
      .flatten(f -> JBIterable.of(f.getChildren()).filter(o -> o.getName().endsWith("test-database-drivers.xml")));
    JBIterable<VirtualFile> real = DGTestUtils.getContent(project, "intellij.database.impl")
      .flatten(e -> e.getSourceFolders(JavaResourceRootType.RESOURCE))
      .filterMap(ContentFolder::getFile)
      .flatten(f -> JBIterable.of(f.getChildren()).filter(o -> o.getName().equals("databaseDrivers")))
      .flatten(f -> JBIterable.of(f.getChildren()).filter(o -> o.getName().endsWith("-drivers.xml")));

    PsiManager psiManager = PsiManager.getInstance(project);
    return td.append(real).filterMap(psiManager::findFile).map(f -> CachedValuesManager.getCachedValue(f, () -> CachedValueProvider.Result.create(parse(f), f)));
  }

  @NotNull
  private static DGTestDrivers parse(@NotNull PsiFile f) {
    return new DGTestDrivers(f.getName(), extract(f));
  }

  @NotNull
  private static List<DGTestDriver> extract(@NotNull PsiFile f) {
    XmlFile xml = ObjectUtils.tryCast(f, XmlFile.class);
    XmlTag rootTag = xml == null ? null : xml.getRootTag();
    if (rootTag == null) return Collections.emptyList();
    List<DGTestDriver> res = new ArrayList<>();
    for (XmlTag driver : rootTag.findSubTags("driver")) {
      ContainerUtil.addIfNotNull(res, parse(driver));
    }
    return res;
  }

  @Nullable
  private static DGTestDriver parse(XmlTag dr) {
    String id = dr.getAttributeValue("id");
    String parentId = dr.getAttributeValue("based-on");
    XmlTag artifact = dr.findFirstSubTag("artifact");
    String artifactName = artifact == null ? null : artifact.getAttributeValue("name");
    String artifactVersion = artifact == null ? null : artifact.getAttributeValue("version");
    return id == null ? null : new DGTestDriver(id, parentId, artifactName, artifactVersion, dr);
  }

  @NotNull
  @Override
  public String getFileName() {
    return fileName;
  }

  @NotNull
  @Override
  public JBIterable<DGTestDriver> getItems() {
    return JBIterable.from(drivers);
  }

  public static class DGTestDriver implements DGTestUtils.ConfigItem {
    public final String id;
    public final String parentId;
    public final String artifactName;
    public final String artifactVersion;
    public final SmartPsiElementPointer<XmlTag> source;
    private final NullableLazyValue<Icon> myIcon;

    public DGTestDriver(String id, String parentId, String artifactName, String artifactVersion, XmlTag source) {
      this.id = id;
      this.parentId = parentId;
      this.artifactName = artifactName;
      this.artifactVersion = artifactVersion;
      this.source = SmartPointerManager.createPointer(source);
      myIcon = DGTestUtils.createDbmsCorneredIcon(id, AllIcons.General.GearPlain);
    }


    @NotNull
    @Override
    public String getName() {
      return id;
    }

    @Nullable
    public Icon getIcon() {
      return myIcon.getValue();
    }

    @Nullable
    @Override
    public XmlTag getSource() {
      return source.getElement();
    }
  }
}
