package com.liuqi.tool.idea.plugin;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 类创建器
 *
 * @author  LiuQi 2019/7/12-10:28
 * @version V1.0
 **/
public class ClassCreator {
    private PsiJavaFile javaFile;
    private Project project;
    private PsiUtils psiUtils;

    private ClassCreator(Project project) {
        this.project = project;
        this.psiUtils = PsiUtils.of(project);
    }

    public static ClassCreator of(Project project) {
        return new ClassCreator(project);
    }

    public ClassCreator init(String name, String content) {
        javaFile = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText(name + ".java", JavaFileType.INSTANCE,
                content);
        return this;
    }

    ClassCreator importClass(String className) {
        if (org.apache.commons.lang3.StringUtils.isBlank(className)) {
            return this;
        }

        psiUtils.findClass(className).ifPresent(javaFile::importClass);
        return this;
    }

    ClassCreator importClassIf(String className, Supplier<Boolean> supplier) {
        if (supplier.get()) {
            importClass(className);
        }

        return this;
    }

    ClassCreator importClassIf(Supplier<String> nameSupplier, Supplier<Boolean> supplier) {
        if (supplier.get()) {
            importClass(nameSupplier.get());
        }

        return this;
    }

    ClassCreator importClass(PsiClass psiClass) {
        if (null == psiClass) {
            return this;
        }

        javaFile.importClass(psiClass);
        return this;
    }

    And addTo(PsiDirectory psiDirectory) {
        return new And(((PsiJavaFile)Optional.ofNullable(psiDirectory.findFile(javaFile.getName())).orElseGet(() -> {
            psiUtils.format(javaFile);
            return (PsiJavaFile)psiDirectory.add(javaFile);
        })).getClasses()[0]);
    }

    ClassCreator copyFields(PsiClass srcClass) {
        PsiClass aClass = javaFile.getClasses()[0];
        PsiElementFactory elementFactory = PsiElementFactory.SERVICE.getInstance(project);
        for (PsiField field : srcClass.getFields()) {
            String name = field.getName();
            assert name != null;
            PsiType type = field.getType();

            psiUtils.findClass(type.getCanonicalText()).ifPresent(typeClass -> psiUtils.importClass(aClass, typeClass));

            String typeName = type.getCanonicalText();
            if (typeName.contains(".")) {
                typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
            }

            // 添加校验注解
            PsiAnnotation psiAnnotation = field.getAnnotation("javax.persistence.Column");
            StringBuilder annotationStringBuilder = new StringBuilder();
            if (null != psiAnnotation) {
                PsiAnnotationMemberValue memberValue = psiAnnotation.findAttributeValue("columnDefinition");
                if (null != memberValue) {
                    String str = memberValue.getText();

                    if (typeName.equals("String")) {
                        // 只有字符串的时候才添加长度限制
                        if (str.contains("varchar") || str.contains("char")) {
                            str = str.replace("varchar(", "").replace("char(", "");
                            int idx = str.indexOf(")");
                            if (-1 != idx) {
                                String lengthStr = str.substring(0, idx).replaceAll("\"", "");
                                if (StringUtils.isNotBlank(lengthStr)) {
                                    int length = Integer.valueOf(lengthStr);
                                    annotationStringBuilder.append("@Length(max = ").append(length).append(") ");
                                    importClass("org.hibernate.validator.constraints.Length");
                                }
                            }
                        }
                    }

                    // 如果是not null，需要加上NotNull校验 javax.validation.constraints
                    if (str.contains("not null")) {
                        if (typeName.equals("String")) {
                            annotationStringBuilder.append("@NotBlank ");
                            importClass("org.hibernate.validator.constraints.NotBlank");
                        } else {
                            annotationStringBuilder.append("@NotNull ");
                            importClass("javax.validation.constraints.NotNull");
                        }
                    }
                }
            }

            PsiField cField = elementFactory.createFieldFromText(annotationStringBuilder.toString() + "private " + typeName + " " + name + ";", null);
            aClass.add(cField);

            // 针对每一个属性生成三个方法
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

        return this;
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

    public static class And {
        private PsiClass psiClass;

        private And(PsiClass psiClass) {
            this.psiClass = psiClass;
        }

        public void and(Consumer<PsiClass> callback) {
            callback.accept(psiClass);
        }
    }
}
