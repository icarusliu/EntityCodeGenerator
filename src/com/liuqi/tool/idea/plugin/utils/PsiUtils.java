package com.liuqi.tool.idea.plugin.utils;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.element.ElementFactory;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * PSI操作辅助类
 *
 * @author LiuQi 2019/7/12-10:22
 * @version V1.0
 **/
public class PsiUtils {
    private Project project;
    private Module module;

    private PsiUtils(Module module) {
        this.module = module;
        this.project = module.getProject();
    }

    public static PsiUtils of(Module module) {
        return new PsiUtils(module);
    }

    public void importClass(PsiClass srcClass, PsiClass... toImportClasses) {
        for (PsiClass toImportClass : toImportClasses) {
            if (null == toImportClass) {
                continue;
            }

            ((PsiJavaFile) srcClass.getContainingFile()).importClass(toImportClass);
        }
    }

    public void createResourceFile(String dirName, String fileName, String content) {
        // 获取目录，在resources目录下，如果没有这个目录，那么创建一个目录
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        List<VirtualFile> sourceRoots = rootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES);
        VirtualFile resourceDirectory = sourceRoots.get(0);
        VirtualFile dir = resourceDirectory.findChild(dirName);
        if (null == dir) {
            try {
                dir = resourceDirectory.createChildDirectory(this, dirName);
            } catch (IOException e) {
                e.printStackTrace();
                dir = resourceDirectory;
            }
        }

        PsiDirectory psiDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(dir);

        PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, FileTypes.PLAIN_TEXT,
                content);
        psiDirectory.add(file);
    }

    public String getPackageName(PsiClass psiClass) {
        return ((PsiJavaFile)psiClass.getContainingFile()).getPackageName();
    }

    public Optional<String> getAnnotationValue(PsiClass psiClass, String annotation, String field) {
        return Optional.ofNullable(psiClass.getAnnotation(annotation)).map(a -> {
            PsiAnnotationMemberValue value = a.findAttributeValue(field);
            if (null != value) {
                return Optional.of(value.getText());
            } else {
                return Optional.<String>empty();
            }
        }).orElse(Optional.empty());
    }

    public Optional<String> getAnnotationValue(PsiAnnotation annotation, String field) {
        return Optional.ofNullable(annotation).map(a -> {
            PsiAnnotationMemberValue value = a.findAttributeValue(field);
            if (null != value) {
                return Optional.of(value.getText());
            } else {
                return Optional.<String>empty();
            }
        }).orElse(Optional.empty());
    }

    public Optional<String> getAnnotationValue(PsiFile psiFile, String annotation, String field) {
        return getAnnotationValue(((PsiJavaFile)psiFile).getClasses()[0], annotation, field);
    }

    public String getPackageAndName(PsiClass psiClass) {
        return ((PsiJavaFile)psiClass.getContainingFile()).getPackageName().concat(".").concat(psiClass.getName());
    }

    public PsiAnnotation addAnnotation(PsiClass psiClass, String annotation) {
        PsiAnnotation psiAnnotation = Objects.requireNonNull(psiClass.getModifierList()).addAnnotation(annotation);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiAnnotation);
        return psiAnnotation;
    }

    public PsiAnnotation addAnnotation(PsiField field, String annotation) {
        PsiAnnotation psiAnnotation = field.getModifierList().addAnnotation(annotation);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiAnnotation);
        return psiAnnotation;
    }

    public void addAnnotationFromStrAfter(PsiClass psiElement, String content, PsiElement posElement) {
        PsiAnnotation psiAnnotation = PsiElementFactory.getInstance(project).createAnnotationFromText(content, null);
        PsiModifierList psiModifierList = psiElement.getModifierList();
        psiModifierList.addAfter(psiAnnotation, posElement);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiAnnotation);
    }

    public void addAnnotationFromStrAfter(PsiField psiElement, String content, PsiElement posElement) {
        PsiAnnotation psiAnnotation = PsiElementFactory.getInstance(project).createAnnotationFromText(content, null);
        PsiModifierList psiModifierList = psiElement.getModifierList();
        psiModifierList.addAfter(psiAnnotation, posElement);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiAnnotation);
    }

    public void addAnnotationFromStrFirst(PsiField psiElement, String content) {
        PsiAnnotation psiAnnotation = PsiElementFactory.getInstance(project).createAnnotationFromText(content, null);
        PsiModifierList psiModifierList = psiElement.getModifierList();
        psiModifierList.addBefore(psiAnnotation, psiModifierList.getFirstChild());
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiAnnotation);
    }

    /**
     * 格式化代码
     *
     * @param psiElement 需要格式化的文件
     */
    public void format(PsiElement psiElement) {
        CodeStyleManager.getInstance(project).reformat(psiElement);
    }

    /**
     * 查找类
     *
     * @param className 类名
     * @return 查找到的类
     */
    public Optional<PsiClass> findClass(String className) {
        return findClass(className, psiClass -> true);
    }

    public Optional<PsiClass> findClass(String className, Predicate<PsiClass> predicate) {
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);

        int idx = className.lastIndexOf(".");
        if (-1 != idx) {
            String packageName = className.substring(0, idx);
            String name = className.substring(idx + 1);
            PsiClass[] classes = shortNamesCache.getClassesByName(name, GlobalSearchScope.allScope(project));

            if (0 != classes.length) {
                for (PsiClass aClass : classes) {
                    PsiJavaFile javaFile = (PsiJavaFile) aClass.getContainingFile();
                    if (javaFile.getPackageName().equals(packageName) && predicate.test(aClass)) {
                        return Optional.of(aClass);
                    }
                }
            }
        } else {
            PsiClass[] classes = shortNamesCache.getClassesByName(className, GlobalSearchScope.allScope(project));
            if (0 != classes.length) {
                for (PsiClass aClass : classes) {
                    if (predicate.test(aClass)) {
                        return Optional.ofNullable(aClass);
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 获取或者创建子目录
     *
     * @param parentDirectory  父级目录
     * @param subDirectoryName 子目录名称
     * @return 查找到的或者创建的子目录名称
     */
    public PsiDirectory getOrCreateSubDirectory(PsiDirectory parentDirectory, String subDirectoryName) {
        return Optional.ofNullable(parentDirectory.findSubdirectory(subDirectoryName)).orElseGet(() -> parentDirectory.createSubdirectory(subDirectoryName));
    }

    /**
     * 为字段增加Setter与Getter方法
     */
    public void addGetterAndSetterMethods(PsiClass aClass) {
        PsiElementFactory elementFactory = PsiElementFactory.SERVICE.getInstance(project);
        for (PsiField field: aClass.getFields()) {
            String name = field.getName();
            PsiType type = field.getType();

            PsiMethod builderSetter = elementFactory.createMethodFromText(createBuilderSetter(aClass.getName(), name, type.getCanonicalText()), field);
            PsiMethod normalSetter = elementFactory.createMethodFromText(createSetter(name, type.getCanonicalText()), field);
            PsiMethod getter = elementFactory.createMethodFromText(createGetter(name, type.getCanonicalText()), field);

            if (0 == aClass.findMethodsByName(builderSetter.getName()).length) {
                aClass.add(builderSetter);
            }

            if (0 == aClass.findMethodsByName(normalSetter.getName()).length) {
                aClass.add(normalSetter);
            }

            if (0 == aClass.findMethodsByName(getter.getName()).length) {
                aClass.add(getter);
            }
        }
    }

    private String createBuilderSetter(String className, String name, String type) {
        return "public " +
                className +
                " " +
                name +
                "(" +
                type +
                " " +
                name +
                ") {" +
                "this." +
                name +
                " = " +
                name +
                ";" +
                "return this;}";
    }

    private String createSetter(@NotNull String name, String type) {
        return "public void set" +
                name.substring(0, 1).toUpperCase() + name.substring(1) +
                "(" +
                type +
                " " +
                name +
                ") {" +
                "this." +
                name +
                " = " +
                name +
                ";}";
    }

    private String createGetter(String name, String type) {
        return "public " +
                type +
                " get" +
                name.substring(0, 1).toUpperCase() + name.substring(1) +
                "() {return this." +
                name +
                ";}";
    }
}
