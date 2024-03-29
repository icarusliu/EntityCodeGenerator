package com.liuqi.tool.idea.plugin.bean;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.PropertiesUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 代码生成配置
 *
 * @author LiuQi 2020/4/21-8:58
 * @version V1.0
 **/
public class GeneratorConfig {
    /**
     * 从codeGenerator.properties中加载配置文件
     */
    public static GeneratorConfig load(Project project) {
        String basePath = project.getBasePath();
        Path path = Paths.get(basePath + "/codeGenerator.properties");
        GeneratorConfig config = new GeneratorConfig();
        if (path.toFile().exists()) {
            try {
                Map<String, String> properties = PropertiesUtil.loadProperties(new FileReader(path.toFile()));
                properties.forEach((k, v) -> {
                    switch (k) {
                        case "common.func.excel":
                            config.setExcelFunc(Boolean.parseBoolean(v));
                            break;
                        case "controller.prefix":
                            config.setControllerPrefix(v);
                            break;
                        case "common.super":
                            config.setWithSuper(Boolean.parseBoolean(v));
                            break;
                        case "common.super.service":
                            config.setSuperService(v);
                            break;
                        case "common.super.controller":
                            config.setSuperController(v);
                            break;
                        case "common.super.dao":
                            config.setSuperDao(v);
                            break;
                        case "ui.enable":
                            config.setWithPage(Boolean.parseBoolean(v));
                            break;
                    }
                });
            } catch (IOException e) {
                System.out.println("加载配置文件失败");
            }
        } else {
            // 配置文件不存在，在项目目录下创建配置文件
            File file = path.toFile();
            try {
                List<String> configs = Arrays.asList("# 是否生成Excel相关功能", "common.func.excel=false",
                        "# Service是否生成接口", "service.interface=false",
                        "# 控制器路径前缀", "controller.prefix=/api");
                Files.write(path, configs, StandardOpenOption.CREATE);
            } catch (IOException e) {
                System.out.println("创建配置文件失败");
            }
        }

        return config;
    }

    /**
     * 是否生成Excel相关功能
     */
    private Boolean excelFunc = false;

    /**
     * 控制器前缀
     */
    private String controllerPrefix = "/api";

    private Boolean withSuper = false;
    private String superService;
    private String superController;
    private String superDao;

    private Boolean withUserId = false;

    /**
     * 就否有deleted字段
     */
    private Boolean withDeleted = false;

    /**
     * 是否有createTime字段
     */
    private Boolean withCreateTime = false;

    /**
     * 是否需要生成前端页面
     */
    private Boolean withPage = false;

    public Boolean getExcelFunc() {
        return excelFunc;
    }

    public GeneratorConfig setExcelFunc(Boolean excelFunc) {
        this.excelFunc = excelFunc;
        return this;
    }

    public String getControllerPrefix() {
        return controllerPrefix;
    }

    public GeneratorConfig setControllerPrefix(String controllerPrefix) {
        this.controllerPrefix = controllerPrefix;
        return this;
    }

    public GeneratorConfig excelFunc(Boolean excelFunc) {
        this.excelFunc = excelFunc;
        return this;
    }

    public GeneratorConfig controllerPrefix(String controllerPrefix) {
        this.controllerPrefix = controllerPrefix;
        return this;
    }

    public GeneratorConfig withSuper(Boolean withSuper) {
        this.withSuper = withSuper;
        return this;
    }

    public void setWithSuper(Boolean withSuper) {
        this.withSuper = withSuper;
    }

    public Boolean getWithSuper() {
        return this.withSuper;
    }

    public GeneratorConfig superService(String superService) {
        this.superService = superService;
        return this;
    }

    public void setSuperService(String superService) {
        this.superService = superService;
    }

    public String getSuperService() {
        return this.superService;
    }

    public GeneratorConfig superController(String superController) {
        this.superController = superController;
        return this;
    }

    public void setSuperController(String superController) {
        this.superController = superController;
    }

    public String getSuperController() {
        return this.superController;
    }

    public GeneratorConfig superDao(String superDao) {
        this.superDao = superDao;
        return this;
    }

    public void setSuperDao(String superDao) {
        this.superDao = superDao;
    }

    public String getSuperDao() {
        return this.superDao;
    }

    public GeneratorConfig withDeleted(Boolean withDeleted) {
        this.withDeleted = withDeleted;
        return this;
    }

    public void setWithDeleted(Boolean withDeleted) {
        this.withDeleted = withDeleted;
    }

    public Boolean getWithDeleted() {
        return this.withDeleted;
    }

    public GeneratorConfig withCreateTime(Boolean withCreateTime) {
        this.withCreateTime = withCreateTime;
        return this;
    }

    public void setWithCreateTime(Boolean withCreateTime) {
        this.withCreateTime = withCreateTime;
    }

    public Boolean getWithCreateTime() {
        return this.withCreateTime;
    }

    public GeneratorConfig withPage(Boolean withPage) {
        this.withPage = withPage;
        return this;
    }

    public void setWithPage(Boolean withPage) {
        this.withPage = withPage;
    }

    public Boolean getWithPage() {
        return this.withPage;
    }

    public Boolean getWithUserId() {
        return withUserId;
    }

    public void setWithUserId(Boolean withUserId) {
        this.withUserId = withUserId;
    }
}
