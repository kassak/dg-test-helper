package com.github.kassak.dg;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
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

public class DGTestDataSources implements DGTestUtils.ConfigFile<DGTestDataSources.DGTestDataSource> {
  public final String fileName;
  public final List<DGTestDataSource> dataSources;

  public DGTestDataSources(String fileName, List<DGTestDataSource> dataSources) {
    this.fileName = fileName;
    this.dataSources = dataSources;
  }

  public static boolean isTestDataSource(@NotNull String name) {
    return name.endsWith("test-data-sources.xml");
  }

  @NotNull
  public static JBIterable<DGTestDataSources> list(@NotNull Project project) {
    JBIterable<VirtualFile> td = DGTestUtils.getContent(project, "intellij.database.tests")
      .flatten(e -> e.getSourceFolders(JavaResourceRootType.TEST_RESOURCE))
      .filterMap(ContentFolder::getFile)
      .flatten(f -> JBIterable.of(f.getChildren()).filter(o -> isTestDataSource(o.getName())));
    PsiManager psiManager = PsiManager.getInstance(project);
    return td.filterMap(psiManager::findFile).map(f -> CachedValuesManager.getCachedValue(f, () -> CachedValueProvider.Result.create(parse(f), f)));
  }

  @NotNull
  private static DGTestDataSources parse(@NotNull PsiFile f) {
    return new DGTestDataSources(f.getName(), extract(f));
  }

  @NotNull
  private static List<DGTestDataSource> extract(@NotNull PsiFile f) {
    XmlFile xml = ObjectUtils.tryCast(f, XmlFile.class);
    XmlTag rootTag = xml == null ? null : xml.getRootTag();
    if (rootTag == null) return Collections.emptyList();
    List<DGTestDataSource> res = new ArrayList<>();
    for (XmlTag dataSource : rootTag.findSubTags("data-source")) {
      ContainerUtil.addIfNotNull(res, parse(dataSource));
    }
    return res;
  }

  @Nullable
  private static DGTestDataSource parse(XmlTag ds) {
    String uuid = ds.getAttributeValue("uuid");
    XmlTag info = ds.findFirstSubTag("database-info");
    String dbms = info == null ? null : info.getAttributeValue("dbms");
    String version = info == null ? null : info.getAttributeValue("exact-version");
    return uuid == null ? null : new DGTestDataSource(uuid, dbms, version, ds);
  }

  @NotNull
  @Override
  public String getFileName() {
    return fileName;
  }

  @NotNull
  @Override
  public JBIterable<DGTestDataSource> getItems() {
    return JBIterable.from(dataSources);
  }

  public static class DGTestDataSource implements DGTestUtils.ConfigItem {
    public final String uuid;
    public final String dbms;
    public final String version;
    public final SmartPsiElementPointer<XmlTag> source;

    public DGTestDataSource(@NotNull String uuid, String dbms, String version, XmlTag source) {
      this.uuid = uuid;
      this.dbms = dbms;
      this.version = version;
      this.source = SmartPointerManager.createPointer(source);
    }

    @Nullable
    public Icon getIcon() {
      DGTestUtils.PresentationHelper ph = ServiceManager.getService(DGTestUtils.PresentationHelper.class);
      return ph == null ? null : ph.getIcon(dbms);
    }

    @NotNull
    @Override
    public String getName() {
      return uuid;
    }

    @Nullable
    @Override
    public XmlTag getSource() {
      return source.getElement();
    }

  }
}
