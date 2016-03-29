/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.archivers.tar;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.utils.ArchiveUtils;

/**
 * This class represents an entry in a Tar archive. It consists
 * of the entry's header, as well as the entry's File. Entries
 * can be instantiated in one of three ways, depending on how
 * they are to be used.
 * <p>
 * TarEntries that are created from the header bytes read from
 * an archive are instantiated with the TarEntry( byte[] )
 * constructor. These entries will be used when extracting from
 * or listing the contents of an archive. These entries have their
 * header filled in using the header bytes. They also set the File
 * to null, since they reference an archive entry not a file.
 * <p>
 * TarEntries that are created from Files that are to be written
 * into an archive are instantiated with the TarEntry( File )
 * constructor. These entries have their header filled in using
 * the File's information. They also keep a reference to the File
 * for convenience when writing entries.
 * <p>
 * Finally, TarEntries can be constructed from nothing but a name.
 * This allows the programmer to construct the entry by hand, for
 * instance when only an InputStream is available for writing to
 * the archive, and the header information is constructed from
 * other information. In this case the header fields are set to
 * defaults and the File is set to null.
 *
 * <p>
 * The C structure for a Tar Entry's header is:
 * <pre>
 * struct header {
 * char name[100];     // TarConstants.NAMELEN    - offset   0
 * char mode[8];       // TarConstants.MODELEN    - offset 100
 * char uid[8];        // TarConstants.UIDLEN     - offset 108
 * char gid[8];        // TarConstants.GIDLEN     - offset 116
 * char size[12];      // TarConstants.SIZELEN    - offset 124
 * char mtime[12];     // TarConstants.MODTIMELEN - offset 136
 * char chksum[8];     // TarConstants.CHKSUMLEN  - offset 148
 * char linkflag[1];   //                         - offset 156
 * char linkname[100]; // TarConstants.NAMELEN    - offset 157
 * The following fields are only present in new-style POSIX tar archives:
 * char magic[6];      // TarConstants.MAGICLEN   - offset 257
 * char version[2];    // TarConstants.VERSIONLEN - offset 263
 * char uname[32];     // TarConstants.UNAMELEN   - offset 265
 * char gname[32];     // TarConstants.GNAMELEN   - offset 297
 * char devmajor[8];   // TarConstants.DEVLEN     - offset 329
 * char devminor[8];   // TarConstants.DEVLEN     - offset 337
 * char prefix[155];   // TarConstants.PREFIXLEN  - offset 345
 * // Used if "name" field is not long enough to hold the path
 * char pad[12];       // NULs                    - offset 500
 * } header;
 * All unused bytes are set to null.
 * New-style GNU tar files are slightly different from the above.
 * For values of size larger than 077777777777L (11 7s)
 * or uid and gid larger than 07777777L (7 7s)
 * the sign bit of the first byte is set, and the rest of the
 * field is the binary representation of the number.
 * See TarUtils.parseOctalOrBinary.
 * </pre>
 *
 * <p>
 * The C structure for a old GNU Tar Entry's header is:
 * <pre>
 * struct oldgnu_header {
 * char unused_pad1[345]; // TarConstants.PAD1LEN_GNU       - offset 0
 * char atime[12];        // TarConstants.ATIMELEN_GNU      - offset 345
 * char ctime[12];        // TarConstants.CTIMELEN_GNU      - offset 357
 * char offset[12];       // TarConstants.OFFSETLEN_GNU     - offset 369
 * char longnames[4];     // TarConstants.LONGNAMESLEN_GNU  - offset 381
 * char unused_pad2;      // TarConstants.PAD2LEN_GNU       - offset 385
 * struct sparse sp[4];   // TarConstants.SPARSELEN_GNU     - offset 386
 * char isextended;       // TarConstants.ISEXTENDEDLEN_GNU - offset 482
 * char realsize[12];     // TarConstants.REALSIZELEN_GNU   - offset 483
 * char unused_pad[17];   // TarConstants.PAD3LEN_GNU       - offset 495
 * };
 * </pre>
 * Whereas, "struct sparse" is:
 * <pre>
 * struct sparse {
 * char offset[12];   // offset 0
 * char numbytes[12]; // offset 12
 * };
 * </pre>
 *
 * <p>
 * The C structure for a xstar (Jörg Schilling star) Tar Entry's header is:
 * <pre>
 * struct star_header {
 *  char name[100];		// offset   0
 *  char mode[8];		// offset 100
 *  char uid[8];		// offset 108
 *  char gid[8];		// offset 116
 *  char size[12];		// offset 124
 *  char mtime[12];		// offset 136
 *  char chksum[8];		// offset 148
 *  char typeflag;		// offset 156
 *  char linkname[100];		// offset 157
 *  char magic[6];		// offset 257
 *  char version[2];		// offset 263
 *  char uname[32];		// offset 265
 *  char gname[32];		// offset 297
 *  char devmajor[8];		// offset 329
 *  char devminor[8];		// offset 337
 *  char prefix[131];		// offset 345
 *  char atime[12];             // offset 476
 *  char ctime[12];             // offset 488
 *  char mfill[8];              // offset 500 
 *  char xmagic[4];             // offset 508  "tar"
 * };
 * </pre>
 * <p>which is identical to new-style POSIX up to the first 130 bytes of the prefix.</p>
 *
 * @NotThreadSafe
 */

public class TarArchiveEntry implements TarConstants, ArchiveEntry {
    public static class TarArchiveEntryData {
		/** The entry's name. */
		public String name;
		/** Whether to enforce leading slashes on the name */
		public boolean preserveLeadingSlashes;
		/** The entry's permission mode. */
		public int mode;
		/** The entry's user id. */
		public long userId;
		/** The entry's group id. */
		public long groupId;
		/** The entry's size. */
		public long size;
		/** The entry's modification time. */
		public long modTime;
		/** If the header checksum is reasonably correct. */
		public boolean checkSumOK;
		/** The entry's link flag. */
		public byte linkFlag;
		/** The entry's link name. */
		public String linkName;
		/** The entry's magic tag. */
		public String magic;
		/** The version of the format */
		public String version;
		/** The entry's user name. */
		public String userName;
		/** The entry's group name. */
		public String groupName;
		/** The entry's major device number. */
		public int devMajor;
		/** The entry's minor device number. */
		public int devMinor;
		/** If an extension sparse header follows. */
		public boolean isExtended;
		/** The entry's real size in case of a sparse file. */
		public long realSize;
		/** is this entry a GNU sparse entry using one of the PAX formats? */
		public boolean paxGNUSparse;
		/** is this entry a star sparse entry using the PAX header? */
		public boolean starSparse;
		/** The entry's file reference */
		public File file;

		public TarArchiveEntryData(String name, long userId, long groupId, long size, String linkName, String magic,
				String version, String groupName, int devMajor, int devMinor) {
			this.name = name;
			this.userId = userId;
			this.groupId = groupId;
			this.size = size;
			this.linkName = linkName;
			this.magic = magic;
			this.version = version;
			this.groupName = groupName;
			this.devMajor = devMajor;
			this.devMinor = devMinor;
		}
	}

	private TarArchiveEntryData data = new TarArchiveEntryData("", 0, 0, 0, "", MAGIC_POSIX, VERSION_POSIX, "", 0, 0);

	/** Maximum length of a user's name in the tar file */
    public static final int MAX_NAMELEN = 31;

    /** Default permissions bits for directories */
    public static final int DEFAULT_DIR_MODE = 040755;

    /** Default permissions bits for files */
    public static final int DEFAULT_FILE_MODE = 0100644;

    /** Convert millis to seconds */
    public static final int MILLIS_PER_SECOND = 1000;

    /**
     * Construct an empty entry and prepares the header values.
     */
    private TarArchiveEntry() {
        String user = System.getProperty("user.name", "");

        if (user.length() > MAX_NAMELEN) {
            user = user.substring(0, MAX_NAMELEN);
        }

        this.data.userName = user;
        this.data.file = null;
    }

    /**
     * Construct an entry with only a name. This allows the programmer
     * to construct the entry's header "by hand". File is set to null.
     *
     * @param name the entry name
     */
    public TarArchiveEntry(String name) {
        this(name, false);
    }

    /**
     * Construct an entry with only a name. This allows the programmer
     * to construct the entry's header "by hand". File is set to null.
     *
     * @param name the entry name
     * @param preserveLeadingSlashes whether to allow leading slashes
     * in the name.
     *
     * @since 1.1
     */
    public TarArchiveEntry(String name, boolean preserveLeadingSlashes) {
        this();

        this.data.preserveLeadingSlashes = preserveLeadingSlashes;

        name = normalizeFileName(name, preserveLeadingSlashes);
        boolean isDir = name.endsWith("/");

        this.data.name = name;
        this.data.mode = isDir ? DEFAULT_DIR_MODE : DEFAULT_FILE_MODE;
        this.data.linkFlag = isDir ? LF_DIR : LF_NORMAL;
        this.data.modTime = new Date().getTime() / MILLIS_PER_SECOND;
        this.data.userName = "";
    }

    /**
     * Construct an entry with a name and a link flag.
     *
     * @param name the entry name
     * @param linkFlag the entry link flag.
     */
    public TarArchiveEntry(String name, byte linkFlag) {
        this(name, linkFlag, false);
    }

    /**
     * Construct an entry with a name and a link flag.
     *
     * @param name the entry name
     * @param linkFlag the entry link flag.
     * @param preserveLeadingSlashes whether to allow leading slashes
     * in the name.
     *
     * @since 1.5
     */
    public TarArchiveEntry(String name, byte linkFlag, boolean preserveLeadingSlashes) {
        this(name, preserveLeadingSlashes);
        this.data.linkFlag = linkFlag;
        if (linkFlag == LF_GNUTYPE_LONGNAME) {
            data.magic = MAGIC_GNU;
            data.version = VERSION_GNU_SPACE;
        }
    }

    /**
     * Construct an entry for a file. File is set to file, and the
     * header is constructed from information from the file.
     * The name is set from the normalized file path.
     *
     * @param file The file that the entry represents.
     */
    public TarArchiveEntry(File file) {
        this(file, file.getPath());
    }

    /**
     * Construct an entry for a file. File is set to file, and the
     * header is constructed from information from the file.
     *
     * @param file The file that the entry represents.
     * @param fileName the name to be used for the entry.
     */
    public TarArchiveEntry(File file, String fileName) {
        String normalizedName = normalizeFileName(fileName, false);
        this.data.file = file;

        if (file.isDirectory()) {
            this.data.mode = DEFAULT_DIR_MODE;
            this.data.linkFlag = LF_DIR;

            int nameLength = normalizedName.length();
            if (nameLength == 0 || normalizedName.charAt(nameLength - 1) != '/') {
                this.data.name = normalizedName + "/";
            } else {
                this.data.name = normalizedName;
            }
        } else {
            this.data.mode = DEFAULT_FILE_MODE;
            this.data.linkFlag = LF_NORMAL;
            this.data.size = file.length();
            this.data.name = normalizedName;
        }

        this.data.modTime = file.lastModified() / MILLIS_PER_SECOND;
        this.data.userName = "";
    }

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     */
    public TarArchiveEntry(byte[] headerBuf) {
        this();
        parseTarHeader(headerBuf);
    }

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @param encoding encoding to use for file names
     * @since 1.4
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     * @throws IOException on error
     */
    public TarArchiveEntry(byte[] headerBuf, ZipEncoding encoding)
        throws IOException {
        this();
        parseTarHeader(headerBuf, encoding);
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param it Entry to be checked for equality.
     * @return True if the entries are equal.
     */
    public boolean equals(TarArchiveEntry it) {
        return getName().equals(it.getName());
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param it Entry to be checked for equality.
     * @return True if the entries are equal.
     */
    @Override
    public boolean equals(Object it) {
        if (it == null || getClass() != it.getClass()) {
            return false;
        }
        return equals((TarArchiveEntry) it);
    }

    /**
     * Hashcodes are based on entry names.
     *
     * @return the entry hashcode
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /**
     * Determine if the given entry is a descendant of this entry.
     * Descendancy is determined by the name of the descendant
     * starting with this entry's name.
     *
     * @param desc Entry to be checked as a descendent of this.
     * @return True if entry is a descendant of this.
     */
    public boolean isDescendent(TarArchiveEntry desc) {
        return desc.getName().startsWith(getName());
    }

    /**
     * Get this entry's name.
     *
     * @return This entry's name.
     */
    public String getName() {
        return data.name;
    }

    /**
     * Set this entry's name.
     *
     * @param name This entry's new name.
     */
    public void setName(String name) {
        this.data.name = normalizeFileName(name, this.data.preserveLeadingSlashes);
    }

    /**
     * Set the mode for this entry
     *
     * @param mode the mode for this entry
     */
    public void setMode(int mode) {
        this.data.mode = mode;
    }

    /**
     * Get this entry's link name.
     *
     * @return This entry's link name.
     */
    public String getLinkName() {
        return data.linkName;
    }

    /**
     * Set this entry's link name.
     *
     * @param link the link name to use.
     *
     * @since 1.1
     */
    public void setLinkName(String link) {
        this.data.linkName = link;
    }

    /**
     * Get this entry's user id.
     *
     * @return This entry's user id.
     * @deprecated use #getLongUserId instead as user ids can be
     * bigger than {@link Integer#MAX_VALUE}
     */
    @Deprecated
    public int getUserId() {
        return (int) (data.userId & 0xffffffff);
    }

    /**
     * Set this entry's user id.
     *
     * @param userId This entry's new user id.
     */
    public void setUserId(int userId) {
        setUserId((long) userId);
    }

    /**
     * Get this entry's user id.
     *
     * @return This entry's user id.
     * @since 1.10
     */
    public long getLongUserId() {
        return data.userId;
    }

    /**
     * Set this entry's user id.
     *
     * @param userId This entry's new user id.
     * @since 1.10
     */
    public void setUserId(long userId) {
        this.data.userId = userId;
    }

    /**
     * Get this entry's group id.
     *
     * @return This entry's group id.
     * @deprecated use #getLongGroupId instead as group ids can be
     * bigger than {@link Integer#MAX_VALUE}
     */
    @Deprecated
    public int getGroupId() {
        return (int) (data.groupId & 0xffffffff);
    }

    /**
     * Set this entry's group id.
     *
     * @param groupId This entry's new group id.
     */
    public void setGroupId(int groupId) {
        setGroupId((long) groupId);
    }

    /**
     * Get this entry's group id.
     *
     * @since 1.10
     * @return This entry's group id.
     */
    public long getLongGroupId() {
        return data.groupId;
    }

    /**
     * Set this entry's group id.
     *
     * @since 1.10
     * @param groupId This entry's new group id.
     */
    public void setGroupId(long groupId) {
        this.data.groupId = groupId;
    }

    /**
     * Get this entry's user name.
     *
     * @return This entry's user name.
     */
    public String getUserName() {
        return data.userName;
    }

    /**
     * Set this entry's user name.
     *
     * @param userName This entry's new user name.
     */
    public void setUserName(String userName) {
        this.data.userName = userName;
    }

    /**
     * Get this entry's group name.
     *
     * @return This entry's group name.
     */
    public String getGroupName() {
        return data.groupName;
    }

    /**
     * Set this entry's group name.
     *
     * @param groupName This entry's new group name.
     */
    public void setGroupName(String groupName) {
        this.data.groupName = groupName;
    }

    /**
     * Convenience method to set this entry's group and user ids.
     *
     * @param userId This entry's new user id.
     * @param groupId This entry's new group id.
     */
    public void setIds(int userId, int groupId) {
        setUserId(userId);
        setGroupId(groupId);
    }

    /**
     * Convenience method to set this entry's group and user names.
     *
     * @param userName This entry's new user name.
     * @param groupName This entry's new group name.
     */
    public void setNames(String userName, String groupName) {
        setUserName(userName);
        setGroupName(groupName);
    }

    /**
     * Set this entry's modification time. The parameter passed
     * to this method is in "Java time".
     *
     * @param time This entry's new modification time.
     */
    public void setModTime(long time) {
        data.modTime = time / MILLIS_PER_SECOND;
    }

    /**
     * Set this entry's modification time.
     *
     * @param time This entry's new modification time.
     */
    public void setModTime(Date time) {
        data.modTime = time.getTime() / MILLIS_PER_SECOND;
    }

    /**
     * Set this entry's modification time.
     *
     * @return time This entry's new modification time.
     */
    public Date getModTime() {
        return new Date(data.modTime * MILLIS_PER_SECOND);
    }

    public Date getLastModifiedDate() {
        return getModTime();
    }

    /**
     * Get this entry's checksum status.
     *
     * @return if the header checksum is reasonably correct
     * @see TarUtils#verifyCheckSum(byte[])
     * @since 1.5
     */
    public boolean isCheckSumOK() {
        return data.checkSumOK;
    }

    /**
     * Get this entry's file.
     *
     * @return This entry's file.
     */
    public File getFile() {
        return data.file;
    }

    /**
     * Get this entry's mode.
     *
     * @return This entry's mode.
     */
    public int getMode() {
        return data.mode;
    }

    /**
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    public long getSize() {
        return data.size;
    }

    /**
     * Set this entry's file size.
     *
     * @param size This entry's new file size.
     * @throws IllegalArgumentException if the size is &lt; 0.
     */
    public void setSize(long size) {
        if (size < 0){
            throw new IllegalArgumentException("Size is out of range: "+size);
        }
        this.data.size = size;
    }

    /**
     * Get this entry's major device number.
     *
     * @return This entry's major device number.
     * @since 1.4
     */
    public int getDevMajor() {
        return data.devMajor;
    }

    /**
     * Set this entry's major device number.
     *
     * @param devNo This entry's major device number.
     * @throws IllegalArgumentException if the devNo is &lt; 0.
     * @since 1.4
     */
    public void setDevMajor(int devNo) {
        if (devNo < 0){
            throw new IllegalArgumentException("Major device number is out of "
                                               + "range: " + devNo);
        }
        this.data.devMajor = devNo;
    }

    /**
     * Get this entry's minor device number.
     *
     * @return This entry's minor device number.
     * @since 1.4
     */
    public int getDevMinor() {
        return data.devMinor;
    }

    /**
     * Set this entry's minor device number.
     *
     * @param devNo This entry's minor device number.
     * @throws IllegalArgumentException if the devNo is &lt; 0.
     * @since 1.4
     */
    public void setDevMinor(int devNo) {
        if (devNo < 0){
            throw new IllegalArgumentException("Minor device number is out of "
                                               + "range: " + devNo);
        }
        this.data.devMinor = devNo;
    }

    /**
     * Indicates in case of an oldgnu sparse file if an extension
     * sparse header follows.
     *
     * @return true if an extension oldgnu sparse header follows.
     */
    public boolean isExtended() {
        return data.isExtended;
    }

    /**
     * Get this entry's real file size in case of a sparse file.
     *
     * @return This entry's real file size.
     */
    public long getRealSize() {
        return data.realSize;
    }

    /**
     * Indicate if this entry is a GNU sparse block.
     *
     * @return true if this is a sparse extension provided by GNU tar
     */
    public boolean isGNUSparse() {
        return isOldGNUSparse() || isPaxGNUSparse();
    }

    /**
     * Indicate if this entry is a GNU or star sparse block using the
     * oldgnu format.
     *
     * @return true if this is a sparse extension provided by GNU tar or star
     * @since 1.11
     */
    public boolean isOldGNUSparse() {
        return data.linkFlag == LF_GNUTYPE_SPARSE;
    }

    /**
     * Indicate if this entry is a GNU sparse block using one of the
     * PAX formats.
     *
     * @return true if this is a sparse extension provided by GNU tar
     * @since 1.11
     */
    public boolean isPaxGNUSparse() {
        return data.paxGNUSparse;
    }

    /**
     * Indicate if this entry is a star sparse block using PAX headers.
     *
     * @return true if this is a sparse extension provided by star
     * @since 1.11
     */
    public boolean isStarSparse() {
        return data.starSparse;
    }

    /**
     * Indicate if this entry is a GNU long linkname block
     *
     * @return true if this is a long name extension provided by GNU tar
     */
    public boolean isGNULongLinkEntry() {
        return data.linkFlag == LF_GNUTYPE_LONGLINK;
    }

    /**
     * Indicate if this entry is a GNU long name block
     *
     * @return true if this is a long name extension provided by GNU tar
     */
    public boolean isGNULongNameEntry() {
        return data.linkFlag == LF_GNUTYPE_LONGNAME;
    }

    /**
     * Check if this is a Pax header.
     *
     * @return {@code true} if this is a Pax header.
     *
     * @since 1.1
     *
     */
    public boolean isPaxHeader(){
        return data.linkFlag == LF_PAX_EXTENDED_HEADER_LC
            || data.linkFlag == LF_PAX_EXTENDED_HEADER_UC;
    }

    /**
     * Check if this is a Pax header.
     *
     * @return {@code true} if this is a Pax header.
     *
     * @since 1.1
     */
    public boolean isGlobalPaxHeader(){
        return data.linkFlag == LF_PAX_GLOBAL_EXTENDED_HEADER;
    }

    /**
     * Return whether or not this entry represents a directory.
     *
     * @return True if this entry is a directory.
     */
    public boolean isDirectory() {
        if (data.file != null) {
            return data.file.isDirectory();
        }

        if (data.linkFlag == LF_DIR) {
            return true;
        }

        if (getName().endsWith("/")) {
            return true;
        }

        return false;
    }

    /**
     * Check if this is a "normal file"
     *
     * @since 1.2
     * @return whether this is a "normal file"
     */
    public boolean isFile() {
        if (data.file != null) {
            return data.file.isFile();
        }
        if (data.linkFlag == LF_OLDNORM || data.linkFlag == LF_NORMAL) {
            return true;
        }
        return !getName().endsWith("/");
    }

    /**
     * Check if this is a symbolic link entry.
     *
     * @since 1.2
     * @return whether this is a symbolic link
     */
    public boolean isSymbolicLink() {
        return data.linkFlag == LF_SYMLINK;
    }

    /**
     * Check if this is a link entry.
     *
     * @since 1.2
     * @return whether this is a link entry
     */
    public boolean isLink() {
        return data.linkFlag == LF_LINK;
    }

    /**
     * Check if this is a character device entry.
     *
     * @since 1.2
     * @return whether this is a character device
     */
    public boolean isCharacterDevice() {
        return data.linkFlag == LF_CHR;
    }

    /**
     * Check if this is a block device entry.
     *
     * @since 1.2
     * @return whether this is a block device
     */
    public boolean isBlockDevice() {
        return data.linkFlag == LF_BLK;
    }

    /**
     * Check if this is a FIFO (pipe) entry.
     *
     * @since 1.2
     * @return whether this is a FIFO entry
     */
    public boolean isFIFO() {
        return data.linkFlag == LF_FIFO;
    }

    /**
     * Check whether this is a sparse entry.
     *
     * @return whether this is a sparse entry
     * @since 1.11
     */
    public boolean isSparse() {
        return isGNUSparse() || isStarSparse();
    }

    /**
     * If this entry represents a file, and the file is a directory, return
     * an array of TarEntries for this entry's children.
     *
     * @return An array of TarEntry's for this entry's children.
     */
    public TarArchiveEntry[] getDirectoryEntries() {
        if (data.file == null || !data.file.isDirectory()) {
            return new TarArchiveEntry[0];
        }

        String[] list = data.file.list();
        TarArchiveEntry[] result = new TarArchiveEntry[list == null ? 0 : list.length];

        for (int i = 0; i < result.length; ++i) {
            result[i] = new TarArchiveEntry(new File(data.file, list[i]));
        }

        return result;
    }

    /**
     * Write an entry's header information to a header buffer.
     *
     * <p>This method does not use the star/GNU tar/BSD tar extensions.</p>
     *
     * @param outbuf The tar entry header buffer to fill in.
     */
    public void writeEntryHeader(byte[] outbuf) {
        try {
            writeEntryHeader(outbuf, TarUtils.DEFAULT_ENCODING, false);
        } catch (IOException ex) {
            try {
                writeEntryHeader(outbuf, TarUtils.FALLBACK_ENCODING, false);
            } catch (IOException ex2) {
                // impossible
                throw new RuntimeException(ex2);
            }
        }
    }

    /**
     * Write an entry's header information to a header buffer.
     *
     * @param outbuf The tar entry header buffer to fill in.
     * @param encoding encoding to use when writing the file name.
     * @param starMode whether to use the star/GNU tar/BSD tar
     * extension for numeric fields if their value doesn't fit in the
     * maximum size of standard tar archives
     * @since 1.4
     * @throws IOException on error
     */
    public void writeEntryHeader(byte[] outbuf, ZipEncoding encoding,
                                 boolean starMode) throws IOException {
        int offset = 0;

        offset = TarUtils.formatNameBytes(data.name, outbuf, offset, NAMELEN,
                                          encoding);
        offset = writeEntryHeaderField(data.mode, outbuf, offset, MODELEN, starMode);
        offset = writeEntryHeaderField(data.userId, outbuf, offset, UIDLEN,
                                       starMode);
        offset = writeEntryHeaderField(data.groupId, outbuf, offset, GIDLEN,
                                       starMode);
        offset = writeEntryHeaderField(data.size, outbuf, offset, SIZELEN, starMode);
        offset = writeEntryHeaderField(data.modTime, outbuf, offset, MODTIMELEN,
                                       starMode);

        int csOffset = offset;

        for (int c = 0; c < CHKSUMLEN; ++c) {
            outbuf[offset++] = (byte) ' ';
        }

        outbuf[offset++] = data.linkFlag;
        offset = TarUtils.formatNameBytes(data.linkName, outbuf, offset, NAMELEN,
                                          encoding);
        offset = TarUtils.formatNameBytes(data.magic, outbuf, offset, MAGICLEN);
        offset = TarUtils.formatNameBytes(data.version, outbuf, offset, VERSIONLEN);
        offset = TarUtils.formatNameBytes(data.userName, outbuf, offset, UNAMELEN,
                                          encoding);
        offset = TarUtils.formatNameBytes(data.groupName, outbuf, offset, GNAMELEN,
                                          encoding);
        offset = writeEntryHeaderField(data.devMajor, outbuf, offset, DEVLEN,
                                       starMode);
        offset = writeEntryHeaderField(data.devMinor, outbuf, offset, DEVLEN,
                                       starMode);

        while (offset < outbuf.length) {
            outbuf[offset++] = 0;
        }

        long chk = TarUtils.computeCheckSum(outbuf);

        TarUtils.formatCheckSumOctalBytes(chk, outbuf, csOffset, CHKSUMLEN);
    }

    private int writeEntryHeaderField(long value, byte[] outbuf, int offset,
                                      int length, boolean starMode) {
        if (!starMode && (value < 0
                          || value >= 1l << 3 * (length - 1))) {
            // value doesn't fit into field when written as octal
            // number, will be written to PAX header or causes an
            // error
            return TarUtils.formatLongOctalBytes(0, outbuf, offset, length);
        }
        return TarUtils.formatLongOctalOrBinaryBytes(value, outbuf, offset,
                                                     length);
    }

    /**
     * Parse an entry's header information from a header buffer.
     *
     * @param header The tar entry header buffer to get information from.
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     */
    public void parseTarHeader(byte[] header) {
        try {
            parseTarHeader(header, TarUtils.DEFAULT_ENCODING);
        } catch (IOException ex) {
            try {
                parseTarHeader(header, TarUtils.DEFAULT_ENCODING, true);
            } catch (IOException ex2) {
                // not really possible
                throw new RuntimeException(ex2);
            }
        }
    }

    /**
     * Parse an entry's header information from a header buffer.
     *
     * @param header The tar entry header buffer to get information from.
     * @param encoding encoding to use for file names
     * @since 1.4
     * @throws IllegalArgumentException if any of the numeric fields
     * have an invalid format
     * @throws IOException on error
     */
    public void parseTarHeader(byte[] header, ZipEncoding encoding)
        throws IOException {
        parseTarHeader(header, encoding, false);
    }

    private void parseTarHeader(byte[] header, ZipEncoding encoding,
                                final boolean oldStyle)
        throws IOException {
        int offset = 0;

        data.name = oldStyle ? TarUtils.parseName(header, offset, NAMELEN)
            : TarUtils.parseName(header, offset, NAMELEN, encoding);
        offset += NAMELEN;
        data.mode = (int) TarUtils.parseOctalOrBinary(header, offset, MODELEN);
        offset += MODELEN;
        data.userId = (int) TarUtils.parseOctalOrBinary(header, offset, UIDLEN);
        offset += UIDLEN;
        data.groupId = (int) TarUtils.parseOctalOrBinary(header, offset, GIDLEN);
        offset += GIDLEN;
        data.size = TarUtils.parseOctalOrBinary(header, offset, SIZELEN);
        offset += SIZELEN;
        data.modTime = TarUtils.parseOctalOrBinary(header, offset, MODTIMELEN);
        offset += MODTIMELEN;
        data.checkSumOK = TarUtils.verifyCheckSum(header);
        offset += CHKSUMLEN;
        data.linkFlag = header[offset++];
        data.linkName = oldStyle ? TarUtils.parseName(header, offset, NAMELEN)
            : TarUtils.parseName(header, offset, NAMELEN, encoding);
        offset += NAMELEN;
        data.magic = TarUtils.parseName(header, offset, MAGICLEN);
        offset += MAGICLEN;
        data.version = TarUtils.parseName(header, offset, VERSIONLEN);
        offset += VERSIONLEN;
        data.userName = oldStyle ? TarUtils.parseName(header, offset, UNAMELEN)
            : TarUtils.parseName(header, offset, UNAMELEN, encoding);
        offset += UNAMELEN;
        data.groupName = oldStyle ? TarUtils.parseName(header, offset, GNAMELEN)
            : TarUtils.parseName(header, offset, GNAMELEN, encoding);
        offset += GNAMELEN;
        data.devMajor = (int) TarUtils.parseOctalOrBinary(header, offset, DEVLEN);
        offset += DEVLEN;
        data.devMinor = (int) TarUtils.parseOctalOrBinary(header, offset, DEVLEN);
        offset += DEVLEN;

        int type = evaluateType(header);
        switch (type) {
        case FORMAT_OLDGNU: {
            offset += ATIMELEN_GNU;
            offset += CTIMELEN_GNU;
            offset += OFFSETLEN_GNU;
            offset += LONGNAMESLEN_GNU;
            offset += PAD2LEN_GNU;
            offset += SPARSELEN_GNU;
            data.isExtended = TarUtils.parseBoolean(header, offset);
            offset += ISEXTENDEDLEN_GNU;
            data.realSize = TarUtils.parseOctal(header, offset, REALSIZELEN_GNU);
            offset += REALSIZELEN_GNU;
            break;
        }
        case FORMAT_XSTAR: {
            String xstarPrefix = oldStyle
                ? TarUtils.parseName(header, offset, PREFIXLEN_XSTAR)
                : TarUtils.parseName(header, offset, PREFIXLEN_XSTAR, encoding);
            if (xstarPrefix.length() > 0) {
                data.name = xstarPrefix + "/" + data.name;
            }
            break;
        }
        case FORMAT_POSIX:
        default: {
            String prefix = oldStyle
                ? TarUtils.parseName(header, offset, PREFIXLEN)
                : TarUtils.parseName(header, offset, PREFIXLEN, encoding);
            // SunOS tar -E does not add / to directory names, so fix
            // up to be consistent
            if (isDirectory() && !data.name.endsWith("/")){
                data.name = data.name + "/";
            }
            if (prefix.length() > 0){
                data.name = prefix + "/" + data.name;
            }
        }
        }
    }

    /**
     * Strips Windows' drive letter as well as any leading slashes,
     * turns path separators into forward slahes.
     */
    private static String normalizeFileName(String fileName,
                                            boolean preserveLeadingSlashes) {
        String osname = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        if (osname != null) {

            // Strip off drive letters!
            // REVIEW Would a better check be "(File.separator == '\')"?

            if (osname.startsWith("windows")) {
                if (fileName.length() > 2) {
                    char ch1 = fileName.charAt(0);
                    char ch2 = fileName.charAt(1);

                    if (ch2 == ':'
                        && (ch1 >= 'a' && ch1 <= 'z'
                            || ch1 >= 'A' && ch1 <= 'Z')) {
                        fileName = fileName.substring(2);
                    }
                }
            } else if (osname.contains("netware")) {
                int colon = fileName.indexOf(':');
                if (colon != -1) {
                    fileName = fileName.substring(colon + 1);
                }
            }
        }

        fileName = fileName.replace(File.separatorChar, '/');

        // No absolute pathnames
        // Windows (and Posix?) paths can start with "\\NetworkDrive\",
        // so we loop on starting /'s.
        while (!preserveLeadingSlashes && fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        return fileName;
    }

    /**
     * Evaluate an entry's header format from a header buffer.
     *
     * @param header The tar entry header buffer to evaluate the format for.
     * @return format type
     */
    private int evaluateType(byte[] header) {
        if (ArchiveUtils.matchAsciiBuffer(MAGIC_GNU, header, MAGIC_OFFSET, MAGICLEN)) {
            return FORMAT_OLDGNU;
        }
        if (ArchiveUtils.matchAsciiBuffer(MAGIC_POSIX, header, MAGIC_OFFSET, MAGICLEN)) {
            if (ArchiveUtils.matchAsciiBuffer(MAGIC_XSTAR, header, XSTAR_MAGIC_OFFSET,
                                              XSTAR_MAGIC_LEN)) {
                return FORMAT_XSTAR;
            }
            return FORMAT_POSIX;
        }
        return 0;
    }

    void fillGNUSparse0xData(Map<String, String> headers) {
        data.paxGNUSparse = true;
        data.realSize = Integer.parseInt(headers.get("GNU.sparse.size"));
        if (headers.containsKey("GNU.sparse.name")) {
            // version 0.1
            data.name = headers.get("GNU.sparse.name");
        }
    }

    void fillGNUSparse1xData(Map<String, String> headers) {
        data.paxGNUSparse = true;
        data.realSize = Integer.parseInt(headers.get("GNU.sparse.realsize"));
        data.name = headers.get("GNU.sparse.name");
    }

    void fillStarSparseData(Map<String, String> headers) {
        data.starSparse = true;
        if (headers.containsKey("SCHILY.realsize")) {
            data.realSize = Long.parseLong(headers.get("SCHILY.realsize"));
        }
    }
}

