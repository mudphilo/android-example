package com.legitimate.AllySuperApp.media;

import android.graphics.Bitmap;

import com.fasterxml.jackson.annotation.JsonIgnore;

import co.tinode.tinodesdk.model.VCard;


/**
 * VCard - contact descriptor. Adds avatar conversion from bits to Android bitmap and back.
 */
public class VxCard extends VCard {
    @JsonIgnore
    public AvatarPhoto avatar;

    public VxCard() {
    }

    public VxCard(String fullName, Bitmap bmp) {
        fn = fullName;
        avatar = new AvatarPhoto(bmp);
        photo = new Photo(avatar.data);
    }

    @Override
    public VxCard copy() {
        VxCard dst = copy(new VxCard(), this);
        dst.avatar = avatar;
        return dst;
    }

    @JsonIgnore
    public Bitmap getBitmap() {
        return (avatar != null) ? avatar.getBitmap() : null;
    }
    @JsonIgnore
    public void setBitmap(Bitmap bmp) {
        avatar = new AvatarPhoto(bmp);
        photo = new Photo(avatar.data);
    }
    @JsonIgnore
    public void setAvatar(AvatarPhoto bmp) {
        avatar = bmp;
        photo = new Photo(avatar.data);
    }

    public void constructBitmap() {
        if (photo != null) {
            avatar = new AvatarPhoto(photo.data);
        }
    }
}
