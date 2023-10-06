package com.github.kassak.dg;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
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

public class DGTestArtifacts implements DGTestUtils.ConfigFile<DGTestArtifacts.DGTestArtifact> {
  public final String fileName;
  public final List<DGTestArtifact> drivers;

  public DGTestArtifacts(String fileName, List<DGTestArtifact> drivers) {
    this.fileName = fileName;
    this.drivers = drivers;
  }

  @NotNull
  public static JBIterable<DGTestArtifacts> list(@NotNull Project project) {
    JBIterable<VirtualFile> td = DGTestUtils.getTestContents(project)
      .flatten(e -> e.getSourceFolders(JavaResourceRootType.TEST_RESOURCE))
      .filterMap(ContentFolder::getFile)
      .flatten(f -> JBIterable.of(f.getChildren()).filter(o -> o.getName().endsWith("test-database-artifacts.xml")));
    JBIterable<VirtualFile> real = DGTestUtils.getContent(project, "intellij.database.connectivity")
      .flatten(e -> e.getSourceFolders(JavaResourceRootType.RESOURCE))
      .filterMap(ContentFolder::getFile)
      .flatten(f -> JBIterable.of(f.getChildren()).filter(o -> o.getName().equals("resources")))
      .flatten(f -> JBIterable.of(f.getChildren()).filter(o -> o.getName().equals("database-artifacts.xml")));

    PsiManager psiManager = PsiManager.getInstance(project);
    return td.append(real).filterMap(psiManager::findFile).map(f -> CachedValuesManager.getCachedValue(f, () -> CachedValueProvider.Result.create(parse(f), f)));
  }

  @NotNull
  private static DGTestArtifacts parse(@NotNull PsiFile f) {
    return new DGTestArtifacts(f.getName(), extract(f));
  }

  @NotNull
  private static List<DGTestArtifact> extract(@NotNull PsiFile f) {
    XmlFile xml = ObjectUtils.tryCast(f, XmlFile.class);
    XmlTag rootTag = xml == null ? null : xml.getRootTag();
    if (rootTag == null) return Collections.emptyList();
    List<DGTestArtifact> res = new ArrayList<>();
    for (XmlTag artifact : rootTag.findSubTags("artifact")) {
      DGTestArtifact parsed = parse(artifact, null);
      if (parsed != null && parsed.version != null) ContainerUtil.addIfNotNull(res, parsed);
      for (XmlTag version : artifact.findSubTags("version")) {
        ContainerUtil.addIfNotNull(res, parse(version, parsed));
      }

    }
    return res;
  }

  @Nullable
  private static DGTestArtifact parse(XmlTag art, DGTestArtifact parent) {
    String id = art.getAttributeValue("id");
    if (id == null) {
      String name = art.getAttributeValue("name");
      if (name != null) {
        id = StringUtil.trimEnd(name.replaceAll("[^a-zA-Z0-9. _-]", ""), " 8");
      }
    }
    if (id == null && parent != null) id = parent.id;
    String version = art.getAttributeValue("version");
    return id == null ? null : new DGTestArtifact(id, version, art);
  }

  @NotNull
  @Override
  public String getFileName() {
    return fileName;
  }

  @NotNull
  @Override
  public JBIterable<DGTestArtifact> getItems() {
    return JBIterable.from(drivers);
  }

  public static class DGTestArtifact implements DGTestUtils.ConfigItem {
    public final String id;
    public final String version;
    public final SmartPsiElementPointer<XmlTag> source;
    private final NullableLazyValue<Icon> myIcon;

    public DGTestArtifact(String id, String version, XmlTag source) {
      this.id = id;
      this.version = version;
      this.source = SmartPointerManager.createPointer(source);
      String id1 = this.id;
      myIcon = DGTestUtils.createDbmsCorneredIcon(id1, AllIcons.Nodes.Artifact);
    }


    @NotNull
    @Override
    public String getName() {
      return id + (version == null ? "" : " " + version);
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
