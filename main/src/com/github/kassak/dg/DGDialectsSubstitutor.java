package com.github.kassak.dg;

import com.intellij.database.Dbms;
import com.intellij.database.util.DbSqlUtil;
import com.intellij.database.util.SqlDialects;
import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutor;
import com.intellij.sql.dialects.SqlLanguageDialect;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DGDialectsSubstitutor extends LanguageSubstitutor {
  private static final Map<String, Dbms> MAPPING = createMapping();

  private static Map<String, Dbms> createMapping() {
    Map<String, Dbms> builder = new LinkedHashMap<>();
    for (Dbms dbms : Dbms.allValues()) {
      builder.put(StringUtil.toLowerCase(dbms.getName()), dbms);
    }
    builder.put("hsql", Dbms.HSQL);
    builder.put("tsql", Dbms.MSSQL);
    builder.put("pg", Dbms.POSTGRES);
    builder.put("postgresql", Dbms.POSTGRES);
    builder.put("chouse", Dbms.CLICKHOUSE);
    return Collections.unmodifiableMap(builder);
  }

  @Nullable
  @Override
  public Language getLanguage(@NotNull VirtualFile file, @NotNull Project project) {
    if (!isDGTestData(project, file)) return null;
    Dbms dbms = getDbms(file, project);
    if (dbms == null) return null;
    SqlLanguageDialect dialect = DbSqlUtil.getSqlDialect(dbms);
    return SqlDialects.getGenericDialect() == dialect ? null : dialect;
  }

  @Nullable
  private static Dbms getDbms(@NotNull VirtualFile file, @NotNull Project project) {
    Dbms byName = detectByFileName(file);
    if (byName != null) return byName;
    for (VirtualFile folder = file.getParent(), top = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
      folder != null && !folder.equals(top); folder = folder.getParent()) {
      Dbms byFolder = detectByFolderName(folder);
      if (byFolder != null) return byFolder;
    }
    return null;
  }

  private static Dbms detectByFolderName(VirtualFile folder) {
    return MAPPING.get(folder.getName());
  }

  private static Dbms detectByFileName(@NotNull VirtualFile file) {
    return detect(file.getName(), String::startsWith);
  }

  @Nullable
  private static Dbms detect(@NotNull String s, @NotNull PairFunction<String, String, Boolean> matcher) {
    for (Map.Entry<String, Dbms> entry : MAPPING.entrySet()) {
      if (matcher.fun(s, entry.getKey())) return entry.getValue();
    }
    return null;
  }

  private static boolean isDGTestData(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file);
    return module != null && module.getName().startsWith("intellij.database") && module.getName().contains("test");
  }

}
