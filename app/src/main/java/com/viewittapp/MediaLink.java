package com.viewittapp;

public class MediaLink {

    private String mediaUrl;
    private String mediaTitle;
    private String cacheKey;

    public MediaLink(String mediaUrl, String mediaTitle) {
        super();
        this.mediaUrl = mediaUrl;
        this.mediaTitle = mediaTitle;
        this.cacheKey = HashUtils.unsaltedHash(mediaUrl);
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getMediaTitle() {
        return mediaTitle;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mediaTitle == null) ? 0 : mediaTitle.hashCode());
        result = prime * result + ((mediaUrl == null) ? 0 : mediaUrl.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MediaLink other = (MediaLink) obj;
        if (mediaTitle == null) {
            if (other.mediaTitle != null) {
                return false;
            }
        } else if (!mediaTitle.equals(other.mediaTitle)) {
            return false;
        }
        if (mediaUrl == null) {
            if (other.mediaUrl != null) {
                return false;
            }
        } else if (!mediaUrl.equals(other.mediaUrl)) {
            return false;
        }
        return true;
    }

}
