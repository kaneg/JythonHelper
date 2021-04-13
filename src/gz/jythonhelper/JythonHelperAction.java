package gz.jythonhelper;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: gongze
 * Date: 12/3/13
 * Time: 10:37 AM
 */
public class JythonHelperAction extends AnAction {
    public void actionPerformed(@NotNull AnActionEvent event) {
        final Project project = event.getData(PlatformDataKeys.PROJECT);
        final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        if (vFiles == null || vFiles.length == 0) {
            Messages.showWarningDialog("No file to be review", "Error");
            return;
        }
        VirtualFile target = vFiles[0];
        JythonHelperConfig config = JythonHelperConfig.getConfig(project);
        JythonHelperConfig.State state = config.getState();
        String targetDirectory = getTargetDirectory(target, project);
        if (targetDirectory == null) {
            return;
        }
        String[] javaLibs = state.javaLibs;
        generatePy(targetDirectory, javaLibs, target.getPath());
        Messages.showInfoMessage("Python files have been generated in directory:" + targetDirectory, "Info");
        LocalFileSystem.getInstance().refresh(false);
    }

    private String getTargetDirectory(VirtualFile vf, Project project) {
        Module module = ModuleUtil.findModuleForFile(vf, project);
        Sdk sdk = PythonSdkUtil.findPythonSdk(module);
        if (sdk == null) {
            Messages.showErrorDialog(project, "No SDK FOUND", "No SDK FOUND");
            return null;
        }
        return PythonSdkUtil.getSkeletonsPath(sdk);
    }

    private void generatePy(String outputDir, String[] libs, String... files) {
        EasyJython ej = new EasyJython(outputDir);
        ej.setLibs(libs);
        ej.generatePys(files);
    }

    @Override
    public void update(AnActionEvent event) {
        final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        setActionEnable(event, vFiles != null && 1 == vFiles.length &&
                (vFiles[0].isDirectory() || "py".equalsIgnoreCase(vFiles[0].getExtension())));
    }

    private void setActionEnable(AnActionEvent event, boolean isEnable) {
        event.getPresentation().setEnabled(isEnable);
    }
}
