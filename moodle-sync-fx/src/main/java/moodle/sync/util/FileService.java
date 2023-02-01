package moodle.sync.util;

import moodle.sync.core.model.json.*;
import moodle.sync.core.model.json.Module;
import moodle.sync.javafx.model.ReturnValue;
import moodle.sync.javafx.model.TimeDateElement;
import moodle.sync.javafx.model.syncTableElement;
import moodle.sync.core.util.MoodleAction;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Objects.isNull;

/**
 * Class implementing several methods in terms of file handling and comparison.
 *
 * @author Daniel Schröter
 */
public class FileService {


    /**
     * Secures that a directory given in a given path exists. Therefore the directory could be created.
     *
     * @param p Path of the directory.
     */
    public static void directoryManager(Path p) throws Exception {
        Files.createDirectories(p);
    }

    /**
     * Method for numbering a sections directory to its position in a moodle course.
     * @param sectionList
     * @param section
     * @return
     */
    public static List<Path> formatSectionFolder(List<Path> sectionList, Section section) {
        int remove = -1;
        for (int i = 0; i < sectionList.size(); i++) {
            String[] sectionFolder = sectionList.get(i).getFileName().toString().split("_", 2);
            if (sectionFolder[sectionFolder.length - 1].equals(section.getName())) {
                File temp = new File(sectionList.get(i).toString());
                temp.renameTo(new File((sectionList.get(i).getParent().toString() + "/" + section.getSection() + "_" + section.getName())));
                remove = i;
                break;
            }
        }
        if (remove != -1) {
            sectionList.remove(remove);
        }
        return sectionList;
    }

    public static List<Path> getPathsInDirectory(Path p) throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
            for (Path entry : stream) {
                result.add(entry);
            }
        } catch (DirectoryIteratorException ex) {
            // I/O error encounted during the iteration, the cause is an IOException
            throw ex.getCause();
        }
        return result;
    }

    public static int findModuleInList(List<Path> list, Module module){
        for(int i = 0; i < list.size(); i++){
            if(list.get(i).getFileName().toString().equals(module.getName())){
                return i;
            }
        }
        return -1;
    }

    public static ReturnValue findResourceInFiles(List<Path> fileList, Module module, int sectionNum, int sectionId,
                                                int position /* Substitute data.size()*/) throws Exception {
        syncTableElement element = null;
        boolean found = false;
        for (int i = 0; i < fileList.size(); i++) {
            if (fileList.get(i).getFileName().toString().equals(module.getContents().get(0).getFilename())) {
                found = true;
                long onlinemodified = module.getContents().get(0).getTimemodified() * 1000;
                long filemodified = Files.getLastModifiedTime(fileList.get(i)).toMillis();
                //Check if local file is newer.
                if (filemodified > onlinemodified) {
                    if (module.getAvailability() != null) {
                        var JsonB = new JsonConfigProvider().getContext(null);
                        JsonB.fromJson(module.getAvailability(), ModuleAvailability.class);
                        LocalDateTime time =
                                LocalDateTime.ofInstant(Instant.ofEpochMilli(JsonB.fromJson(module.getAvailability().replaceAll("\\\\", ""),
                                        ModuleAvailability.class).getTimeDateCondition().getT() * 1000L), ZoneId.systemDefault());
                        element = new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId,
                                position, module.getModname(), fileList.get(i), true, false,
                                MoodleAction.MoodleSynchronize, getPriorityVisibility(module.getVisible() == 1,
                                JsonB.fromJson(module.getAvailability().replaceAll("\\\\", ""),
                                        ModuleAvailability.class).getConditionVisibility()),
                                new TimeDateElement(time.toLocalDate(), time.toLocalTime()), module.getId());
                    } else {
                        element = new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId,
                                position, module.getModname(), fileList.get(i), true, false,
                                MoodleAction.MoodleSynchronize, module.getVisible() == 1, module.getId());
                    }
                    fileList.remove(i);
                    break;
                } else {
                    if (module.getAvailability() != null) {
                        var JsonB = new JsonConfigProvider().getContext(null);
                        JsonB.fromJson(module.getAvailability(), ModuleAvailability.class);
                        LocalDateTime time =
                                LocalDateTime.ofInstant(Instant.ofEpochMilli(JsonB.fromJson(module.getAvailability().replaceAll("\\\\", ""),
                                        ModuleAvailability.class).getTimeDateCondition().getT() * 1000L), ZoneId.systemDefault());
                        element = new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId,
                                position, module.getModname(), fileList.get(i), false, false,
                                MoodleAction.ExistingFile, getPriorityVisibility(module.getVisible() == 1,
                                JsonB.fromJson(module.getAvailability().replaceAll("\\\\", ""),
                                        ModuleAvailability.class).getConditionVisibility()),
                                new TimeDateElement(time.toLocalDate(), time.toLocalTime()));
                    }
                    else {
                        element = new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId,
                                position, module.getModname(), fileList.get(i), false, false,
                                MoodleAction.ExistingFile, module.getVisible() == 1);
                    }
                    fileList.remove(i);
                    break;
                }
            }
        }
        if (!found) {
            element = new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId, position,
                    module.getModname(), false, false, MoodleAction.NotLocalFile, module.getVisible() == 1);
        }

        return new ReturnValue(fileList, element);
    }

    public static syncTableElement checkDirectoryForUpdates(Path path, Module module, int sectionNum, int sectionId,
                                                      int position, String formatsMoodle) throws Exception{
        List<Path> newContent = new ArrayList<>(List.of());
        List<Path> localContent = FileService.getPathsInDirectory(path);
        for(Path localFile : localContent){
            if(formatsMoodle.contains(FilenameUtils.getExtension(String.valueOf(localFile)))) {
                boolean found = false;
                for (Content moduleFile : module.getContents()) {
                    if (localFile.getFileName().toString().equals(moduleFile.getFilename())) {
                        long onlinemodified = moduleFile.getTimemodified() * 1000;
                        long filemodified = Files.getLastModifiedTime(localFile).toMillis();
                        if (filemodified < onlinemodified) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    newContent.add(localFile);
                }
            }
        }
        if(newContent.size() > 0) {
            var JsonB = new JsonConfigProvider().getContext(null);
            TimeDateElement availability = null;

            if(module.getAvailability() != null) {
                LocalDateTime time =
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(JsonB.fromJson(module.getAvailability().replaceAll("\\\\", ""),
                                ModuleAvailability.class).getTimeDateCondition().getT() * 1000L), ZoneId.systemDefault());
                availability = new TimeDateElement(time.toLocalDate(), time.toLocalTime());
                return new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId, position,
                        module.getModname(),path,true, false, MoodleAction.FolderSynchronize,
                        FileService.getPriorityVisibility(module.getVisible() == 1, JsonB.fromJson(module.getAvailability().replaceAll("\\\\", ""),
                                ModuleAvailability.class).getConditionVisibility()) ,
                        module.getId(), availability, newContent, module.getContextid());
            } else {
                return new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId, position,
                        module.getModname(),path,true, false, MoodleAction.FolderSynchronize,module.getVisible() == 1,
                        module.getId(), availability, newContent, module.getContextid());
            }
        }
        else {
            return new syncTableElement(module.getName(), module.getId(), sectionNum, sectionId, position,
                    module.getModname(), path, false, false, MoodleAction.ExistingFile,
                    module.getVisible() == 1);
        }
    }


    public static Boolean getPriorityVisibility(Boolean visible, Boolean availability) {
        if (!visible || !availability) {
            return false;
        }
        return true;
    }

    public static List<List<Path>> sortDirectoryFiles(List<Path> directoryFiles, String formatsMoodle,
                                                String formatsFileserver) {

        List<Path> moodleElements = new ArrayList<>(List.of());
        List<Path> fileserverElements = new ArrayList<>(List.of());
        List<Path> directories = new ArrayList<>(List.of());
        List<Path> notSupportedElements = new ArrayList<>(List.of());

        for(Path entry : directoryFiles){
            if(Files.isDirectory(entry)){
                directories.add(entry);
            }
            else if(formatsMoodle.contains(FilenameUtils.getExtension(String.valueOf(entry)))){
                moodleElements.add(entry);
            }
            else if(formatsFileserver.contains(FilenameUtils.getExtension(String.valueOf(entry)))){
                fileserverElements.add(entry);
            }
            else {
                notSupportedElements.add(entry);
            }
        }

        List<List<Path>> result = new ArrayList<>();
        result.add(moodleElements);
        result.add(fileserverElements);
        result.add(directories);
        result.add(notSupportedElements);

        return result;
    }

    public static boolean contains(final String[] arr, final String key) {
        return Arrays.asList(arr).contains(key);
    }

}

