/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.video;

/**
 * Video constants.
 */
public class VideoConstants {

    public static final String VIDEO_TYPE = "Video";

    public static final String VIDEO_FACET = "Video";

    public static final String EXTERNAL_VIDEO_FACET = "Video";

    public static final String HAS_STORYBOARD_FACET = "HasStoryboard";

    public static final String HAS_VIDEO_PREVIEW_FACET = "HasVideoPreview";

    public static final String STORYBOARD_PROPERTY = "vid:storyboard";

    public static final String DURATION_PROPERTY = "vid:info/duration";

    public static final String EXTERNAL_VIDEO_EXTERNAL_ID_PROPERTY = "exvid:externalId";

    public static final String EXTERNAL_VIDEO_VIDEO_PROVIDER_NAME_PROPERTY = "exvid:videoProviderName";

    public static final String EXTERNAL_VIDEO_STATUS_PROPERTY = "exvid:status";

    public static final String VIDEO_CHANGED_PROPERTY = "videoChanged";

    // Constant utility class.
    private VideoConstants() {
    }

}