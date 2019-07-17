
使用Spring开发项目的过程中，习惯于使用JPA进行表的创建、数据的更新等操作，但JPA对于复杂查询的支持比较差，因此很多时候又会同时引入mybatis来进行复杂查询。
再加上实体对象与传输对象分离，在开发一个简单的表单查询维护功能时，也需要创建很多的类来完成对应功能的开发，如DTO对象、数据库操作类、DTO与实体类转换器、操作服务接口、操作服务接口实现类、控制器等。每次我们新创建一个实体类，都需要重复这个过程。一般情况下，我们可以预先定义好各个基础类或者接口，当需要创建一个新的实体类时，再基于这些基础类与接口一个个去创建所需要的类与接口，实际上代码量也不会很大，但这个过程很烦人，很多重复机械的步骤需要执行。尤其是根据实体类去创建一个属性一样的dto对象，以及编写myBatis的ResultMap及基础的select语句，很繁琐而且非常容易出错。个人实在忍受不了这种重复性的动作，于是下定决心一定要让自己逃离这种状况。

之前使用过JHipster，通过这个工具，可以简化这个过程，我们可以只需要定义一个配置文件，然后执行JHipster的命令就可以完成所有相关类的创建，但个人对于这个东西不是很感冒，于是想是否可以通过IDEA的插件来自动生成这些代码？找了一圈没有发现比较好用的插件，于是只好自己研究写了一个插件。
先看下为这个插件定义的三个关键目标：
- 根据Entity自动创建DTO对象、转换器对象、MyBatis的Dao接口、JPA的Repository接口、服务接口及服务实现类、控制器类；
- 在服务接口、服务接口实现类、控制器中实现对对象的基本增、删、改、查操作；
- 根据Entity自动生成Mybatis的映射文件，并在文件中根据Entity的属性自动生成ResultMap映射以及基本的查询语句；

目前已经基本开发完成，它使用在Spring Boot的项目中，要求项目中引入了JPA与Map Struct、PageHelper、MyBatis等依赖。
先来看下它的使用与效果：

首先，需要定义一个Entity类，这个插件要求Entity类上一定要有Entity注解，否则不会有任何效果，定义如下图所示：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190716181937985.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2ljYXJ1c2xpdQ==,size_16,color_FFFFFF,t_70)

然后，打开这个Entity类，确保这个类在编辑器中是当前正在编辑的类，选择Windows -> Entity Code Generator菜单（或者也可以为其指定快捷键），此时会看到在包中生成了多个包及相关类：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190716181951322.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2ljYXJ1c2xpdQ==,size_16,color_FFFFFF,t_70)![在这里插入图片描述](https://img-blog.csdnimg.cn/20190716182004355.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2ljYXJ1c2xpdQ==,size_16,color_FFFFFF,t_70)生成的这些类中包含有一些基本的功能，如基本的增删改查等。生成后，如果需要更加复杂的功能，就可以在生成的这些类中继续编写代码去实现了。

**是不是很简单？只需要定义好Entity然后再按一个快捷键，所有需要的类都会自动创建完成。而且最关键的是，生成的DTO对象会默认包含有所有Entity对象的属性，并且自动添加非空及长度校验，另外，还会自动在resources/mappers目录下添加mybatis的映射文件，会自动根据实体类的属性生成对应的resultMap与基础的select语句**


目前插件生成的类清单如下：
- *Repository: 数据库操作类；
    - 生成目录：../repository
    - 生成说明：如果项目中包含有BaseRepository类，那么新创建的Repository对象将会继承自该对象，否则，会自动生成一个BaseRepository并继承自该类；其中
                BaseRepository继承了JpaRepository与JpaSpecificationExecutor两个接口
- *DTO：数据传输对象;
    - 生成目录：../../service/dto
    - 生成说明：根据Entity中的属性自动生成DTO的属性及其Getter与Setter方法，如果Entity中某个属性中包含有Column注解，并且注解中包含有Not Null或者Varchar(255)等限制，
                则生成的DTO对象会自动添加NotBlank、NotNull、Length等校验注解。另外，如果项目中包含有AbstractBaseDTO类，那么生成的DTO对象将会继承自该类，否则不会继承任何类；
- *Mapper: DTO与Entity转换类
    - 生成目录：../../service/mapper
    - 生成说明：如果项目中包含有EntityMapper类，那么新创建的转换类将继承自该类，否则创建一个EntityMapper类并继承它来生成新的转换类；    
- *Service: 实体类操作接口
    - 生成目录：../../service
    - 生成说明：如果项目中没有则生成EntityService接口，新创建的实体类操作接口将继承原本存在或者新创建的EntityService接口；生成的接口中包含有save/delete/findAll/
                findOne等常用接口
- *ServiceImpl: 实体类操作接口实现类
    - 生成目录：../../service/impl
    - 生成说明：如果项目中有AbstractBaseEntityService，那么新创建的实体类将会继承自该类，否则不会继承该类。
- *Controller：控制器类；
	- 生成目录：../../controller或者../../web/rest或者../../web中，如果项目中没有这三个目录，则默认会创建controller目录并将控制器生成在其中；
	- 生成说明：会生成基本的增删改查接口，如果在项目中有引用Swagger相关依赖，会自动添加Api相关注解。

当然这个插件还有很多需要优化的地方。后续有时间再继续完善吧，感兴趣的可以试用下，如果觉得有用的话，反馈一下给我我也会有更多的动力去完善它了，当然这个插件也就花了几个小时完成的，如果存在什么问题或者不合理的地方也请不吝赐教。

打包后的插件包见工程目录的entityCodeGenerator.jar，直接通过idea的本地安装插件方式安装即可，安装完后会在window菜单下增加菜单项。

感兴趣或者有相关建议或者使用方面有疑问的可以关注我的个人网站进行留言： [个人网站](liumoran.cn)