package net.exent.flywithme.server.tools;

import net.exent.flywithme.server.util.NoaaProxy;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by canidae on 12/2/16.
 */

public class CaptchaTester {
    public static void main(String... args) throws Exception {
        DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(args[0]), "*.gif");
        for (Path path : stream) {
            String captcha = NoaaProxy.solveCaptcha(Files.readAllBytes(path));
            System.out.println((path.getFileName().toString().equals(captcha + ".gif") ? "CORRECT: " : "WRONG: ") + path.getFileName() + " - " + captcha);
        }
        stream.close();
    }
}
