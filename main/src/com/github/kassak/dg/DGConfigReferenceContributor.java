package com.github.kassak.dg;

import com.github.kassak.dg.DGTestArtifacts.DGTestArtifact;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StringPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.XmlPatterns.*;

public class DGConfigReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(driverRefValue(), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
        return ContainerUtil.ar(new DGDriverTagReference((XmlTag)psiElement));
      }
    });
    registrar.registerReferenceProvider(driverBaseValue(), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
        return ContainerUtil.ar(new DGDriverAttributeReference((XmlAttributeValue)psiElement));
      }
    });
    registrar.registerReferenceProvider(driverArtifactIdValue(), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
        return ContainerUtil.ar(new DGArtifactIdAttributeReference((XmlAttributeValue)psiElement));
      }
    });
    registrar.registerReferenceProvider(driverArtifactVersionValue(), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
        return ContainerUtil.ar(new DGArtifactVersionAttributeReference((XmlAttributeValue)psiElement));
      }
    });
  }

  @NotNull
  private ElementPattern<XmlTag> driverRefValue() {
    return xmlTag().withName("driver-ref")
      .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().with(
        new PatternCondition<String>("DGTestDataSources.isTestDataSource") {
          @Override
          public boolean accepts(@NotNull String s, ProcessingContext processingContext) {
            return DGTestDataSources.isTestDataSource(s);
          }
        })));
  }

  @NotNull
  private ElementPattern<XmlAttributeValue> driverBaseValue() {
    return xmlAttributeValue("based-on")
      .withSuperParent(2, xmlTag().withName("driver"))
      .inFile(PlatformPatterns.psiFile().withName(isTestDatabaseDrivers()));
  }

  @NotNull
  private ElementPattern<XmlAttributeValue> driverArtifactIdValue() {
    return xmlAttributeValue("id")
      .withSuperParent(2, xmlTag().withName("artifact"))
      .inFile(PlatformPatterns.psiFile().withName(isTestDatabaseDrivers()));
  }

  @NotNull
  private ElementPattern<XmlAttributeValue> driverArtifactVersionValue() {
    return xmlAttributeValue("version")
      .withSuperParent(2, xmlTag().withName("artifact"))
      .inFile(PlatformPatterns.psiFile().withName(isTestDatabaseDrivers()));
  }

  @NotNull
  private StringPattern isTestDatabaseDrivers() {
    return PlatformPatterns.string().with(
      new PatternCondition<String>("DGTestDrivers.isTestDatabaseDrivers") {
        @Override
        public boolean accepts(@NotNull String s, ProcessingContext processingContext) {
          return DGTestDrivers.isTestDatabaseDrivers(s);
        }
      });
  }

  private static class DGDriverTagReference extends TagValueReference implements DGDriverReferenceMixin {
    public DGDriverTagReference(@NotNull XmlTag element) {
      super(element);
    }
  }

  private static class DGDriverAttributeReference extends AttrValueReference implements DGDriverReferenceMixin {
    public DGDriverAttributeReference(@NotNull XmlAttributeValue element) {
      super(element);
    }
  }

  private static class DGArtifactVersionAttributeReference extends AttrValueReference implements PsiPolyVariantReference {
    public DGArtifactVersionAttributeReference(@NotNull XmlAttributeValue element) {
      super(element);
    }

    @Override
    public @Nullable PsiElement resolve() {
      ResolveResult[] res = multiResolve(false);
      return res.length == 0 ? null : res[0].getElement();
    }

    @NotNull
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean b) {
      String ver = getCanonicalText();
      return getArtifacts()
        .filter(a -> ver.equals(a.version))
        .filterMap(a -> {
          XmlTag source = a.getSource();
          return source == null ? null : (ResolveResult)new PsiElementResolveResult(source);
        })
        .toArray(ResolveResult.EMPTY_ARRAY);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String s) throws IncorrectOperationException {
      return null;
    }

    @NotNull
    @Override
    public Object @NotNull [] getVariants() {
      return getArtifacts()
        .map(a -> (Object) LookupElementBuilder.create(a.version).withIcon(a.getIcon()))
        .toArray(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }

    @NotNull
    private JBIterable<DGTestArtifact> getArtifacts() {
      JBIterable<DGTestArtifact> artifacts = DGTestArtifacts.list(getElement().getProject())
        .flatten(DGTestArtifacts::getItems);
      String id = getId();
      return id == null ? artifacts : artifacts.filter(a -> id.equals(a.id));
    }

    private String getId() {
      //todo: go through bases
      XmlTag tag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
      return tag == null ? null : tag.getAttributeValue("id");
    }
  }

  private static class DGArtifactIdAttributeReference extends AttrValueReference implements PsiPolyVariantReference {
    public DGArtifactIdAttributeReference(@NotNull XmlAttributeValue element) {
      super(element);
    }

    @Override
    public @Nullable PsiElement resolve() {
      ResolveResult[] res = multiResolve(false);
      return res.length == 0 ? null : res[0].getElement();
    }

    @NotNull
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean b) {
      String id = getCanonicalText();
      return DGTestArtifacts.list(getElement().getProject())
        .flatten(DGTestArtifacts::getItems)
        .filter(a -> id.equals(a.id))
        .filterMap(a -> {
          XmlTag source = a.getSource();
          return source == null ? null : (ResolveResult)new PsiElementResolveResult(source);
        })
        .toArray(ResolveResult.EMPTY_ARRAY);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String s) throws IncorrectOperationException {
      return null;
    }

    @NotNull
    @Override
    public Object @NotNull [] getVariants() {
      return DGTestArtifacts.list(getElement().getProject())
        .flatten(DGTestArtifacts::getItems)
        .map(a -> (Object) LookupElementBuilder.create(a.id).withIcon(a.getIcon()))
        .toArray(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
  }

  private interface DGDriverReferenceMixin extends PsiReference {
    @Override
    default @Nullable PsiElement resolve() {
      String text = getCanonicalText();
      DGTestDrivers.DGTestDriver driver = DGTestDrivers.list(getElement().getProject())
        .flatten(DGTestDrivers::getItems)
        .find(d -> text.equals(d.getName()));
      return driver == null ? null : driver.getSource();
    }

    @Override
    default PsiElement handleElementRename(@NotNull String s) throws IncorrectOperationException {
      //todo
      return null;
    }

    @NotNull
    @Override
    default Object @NotNull [] getVariants() {
      return DGTestDrivers.list(getElement().getProject()).flatten(DGTestDrivers::getItems)
        .map(d -> (Object)LookupElementBuilder.create(d.getName()).withIcon(d.getIcon()))
        .toArray(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
  }

  private static abstract class AttrValueReference implements PsiReference {
    private final XmlAttributeValue myElement;

    public AttrValueReference(@NotNull XmlAttributeValue element) {
      myElement = element;
    }

    @Override
    public @NotNull XmlAttributeValue getElement() {
      return myElement;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
      return myElement.getValueTextRange().shiftLeft(myElement.getTextRange().getStartOffset());
    }

    @Override
    public @NotNull String getCanonicalText() {
      return StringUtil.notNullize(myElement.getValue());
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement psiElement) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement psiElement) {
      PsiElement resolve = resolve();
      return resolve != null && psiElement.isEquivalentTo(resolve);
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }

  private static abstract class TagValueReference implements PsiReference {
    private final XmlTag myElement;

    public TagValueReference(@NotNull XmlTag element) {
      myElement = element;
    }

    @Override
    public @NotNull PsiElement getElement() {
      return myElement;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
      return myElement.getValue().getTextRange().shiftLeft(myElement.getTextRange().getStartOffset());
    }

    @Override
    public @NotNull String getCanonicalText() {
      return myElement.getValue().getText();
    }

    public Project getProject() {
      return myElement.getProject();
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement psiElement) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement psiElement) {
      PsiElement resolve = resolve();
      return resolve != null && psiElement.isEquivalentTo(resolve);
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }
}
