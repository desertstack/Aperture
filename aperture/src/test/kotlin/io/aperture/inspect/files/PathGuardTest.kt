package io.aperture.inspect.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Aperture serves the app's file sandbox over a port that, by default, answers on every network
 * interface. Anything that reads a path the console chose is a way out of that sandbox and into
 * the rest of the device. No runner here on purpose: this is plain logic and it should stay
 * fast enough that nobody is tempted to skip it.
 */
class PathGuardTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File

    private fun setUpTree(): File {
        root = temp.newFolder("files")
        File(root, "notes").mkdirs()
        File(root, "notes/todo.txt").writeText("hello")
        return root
    }

    @Test
    fun `a path inside the root resolves`() {
        val root = setUpTree()

        val resolved = PathGuard.resolveWithin(root, "notes/todo.txt")

        assertNotNull(resolved)
        assertEquals("hello", resolved!!.readText())
    }

    @Test
    fun `the root itself resolves`() {
        val root = setUpTree()

        assertEquals(root.canonicalFile, PathGuard.resolveWithin(root, ""))
    }

    @Test
    fun `climbing out with dot dot is refused`() {
        val root = setUpTree()

        assertNull(PathGuard.resolveWithin(root, "../"))
        assertNull(PathGuard.resolveWithin(root, "../../etc/hosts"))
        assertNull(PathGuard.resolveWithin(root, "notes/../../secret"))
        assertNull(PathGuard.resolveWithin(root, "../../../../../../../../etc/passwd"))
    }

    @Test
    fun `an absolute path is refused`() {
        val root = setUpTree()

        assertNull(PathGuard.resolveWithin(root, "/etc/hosts"))
        assertNull(PathGuard.resolveWithin(root, "/data/data/other.app/databases/app.db"))
    }

    @Test
    fun `a sibling whose name merely starts with the root is refused`() {
        // Without the separator in the comparison, root ".../files" would accept
        // ".../files_secret". This is the classic way the check is written wrong.
        val root = setUpTree()
        val sibling = temp.newFolder("files_secret")
        File(sibling, "keys.txt").writeText("secret")

        assertNull(PathGuard.resolveWithin(root, "../files_secret/keys.txt"))
    }

    @Test
    fun `a symbolic link pointing out of the root is refused`() {
        val root = setUpTree()
        val outside = temp.newFolder("outside")
        File(outside, "target.txt").writeText("secret")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())

        assertNull(PathGuard.resolveWithin(root, "escape/target.txt"))
    }

    @Test
    fun `a symbolic link staying inside the root is allowed`() {
        val root = setUpTree()
        Files.createSymbolicLink(File(root, "shortcut").toPath(), File(root, "notes").toPath())

        val resolved = PathGuard.resolveWithin(root, "shortcut/todo.txt")

        assertNotNull(resolved)
        assertEquals("hello", resolved!!.readText())
    }

    @Test
    fun `a NUL in the path is refused`() {
        val root = setUpTree()

        assertNull(PathGuard.resolveWithin(root, "notes/todo.txt\u0000.png"))
    }

    @Test
    fun `a root that is itself reached through a link still matches`() {
        // On a device /data/data/<pkg> is a symbolic link to /data/user/0/<pkg>. Canonicalizing
        // only one side would make every path fail, or every path pass.
        val real = temp.newFolder("real")
        File(real, "a.txt").writeText("hi")
        val link = File(temp.root, "linked")
        Files.createSymbolicLink(link.toPath(), real.toPath())

        val resolved = PathGuard.resolveWithin(link, "a.txt")

        assertNotNull(resolved)
        assertEquals("hi", resolved!!.readText())
    }

    @Test
    fun `a path relative to the root comes back`() {
        val root = setUpTree()

        assertEquals("notes/todo.txt", PathGuard.relativize(root, File(root, "notes/todo.txt")))
        assertEquals("", PathGuard.relativize(root, root))
        assertNull(PathGuard.relativize(root, temp.root))
    }
}
