package com.liuqi.tool.idea.plugin;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.psi.*;
import com.liuqi.tool.idea.plugin.utils.PsiUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 类创建器
 *
 * @author  LiuQi 2019/7/12-10:28
 * @version V1.0
 **/
class ClassCreator {
    private PsiJavaFile javaFile;
    private Project project;
    private PsiUtils psiUtils;

    private ClassCreator(Module module) {
        this.psiUtils = PsiUtils.of(module);
        this.project = module.getProject();
    }

    static ClassCreator of(Module module) {
        return new ClassCreator(module);
    }

    ClassCreator init(String name, String content) {
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

    ClassCreator addGetterAndSetterMethods() {
        PsiClass aClass = javaFile.getClasses()[0];
        psiUtils.addGetterAndSetterMethods(aClass);

        return this;
    }

    ClassCreator copyFields(PsiClass srcClass) {
        PsiClass aClass = javaFile.getClasses()[0];
        PsiElementFactory elementFactory = PsiElementFactory.SERVICE.getInstance(project);
        for (PsiField field : srcClass.getFields()) {
            String name = field.getName();
            PsiType type = field.getType();

            psiUtils.findClass(type.getCanonicalText()).ifPresent(typeClass -> psiUtils.importClass(aClass, typeClass));

            String typeName = type.getCanonicalText();
            if (typeName.contains(".")) {
                typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
            }

            // 添加校验注解
            PsiAnnotation psiAnnotation = field.getAnnotation("javax.persistence.Column");
            StringBuilder annotationStringBuilder = new StringBuilder("");
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
                                    int length = Integer.parseInt(lengthStr);
                                    annotationStringBuilder.append("@Length(max = ").append(length).append(") ");
                                    importClass("org.hibernate.validator.constraints.Length");
                                }
                            }
                        }
                    }

                    // 如果是not null，需要加上NotNull校验 javax.validation.constraints
                    if (str.contains("not null") && !typeName.toLowerCase().contains("type")) {
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

            if (typeName.toLowerCase().equals("localdate")) {
                annotationStringBuilder.append("@JsonFormat(pattern = \"yyyy-MM-dd\") ");
                importClass("com.fasterxml.jackson.annotation.JsonFormat");
            } else if (typeName.toLowerCase().equals("localdatetime")) {
                annotationStringBuilder.append("@JsonFormat(pattern = \"yyyy-MM-dd HH:mm:ss\") ");
                importClass("com.fasterxml.jackson.annotation.JsonFormat");
            }

            PsiField cField = elementFactory.createFieldFromText(annotationStringBuilder.toString() + "private " + typeName + " " + name + ";\n", null);
            aClass.add(cField);

            // 针对每一个属性生成三个方法
            psiUtils.addGetterAndSetterMethods(aClass);
            psiUtils.format(aClass);
        }

        return this;
    }

    public static class And {
        private PsiClass psiClass;

        private And(PsiClass psiClass) {
            this.psiClass = psiClass;
        }

        void and(Consumer<PsiClass> callback) {
            callback.accept(psiClass);
        }
    }
}
