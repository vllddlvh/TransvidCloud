package storage

import "testing"

func TestContentTypeFor(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{"hls/360p/index.m3u8", "application/vnd.apple.mpegurl"},
		{"hls/360p/seg_000.ts", "video/mp2t"},
		{"thumb/default.jpg", "image/jpeg"},
		{"something.bin", "application/octet-stream"},
	}
	for _, tt := range tests {
		if got := ContentTypeFor(tt.name); got != tt.want {
			t.Errorf("ContentTypeFor(%q) = %q, want %q", tt.name, got, tt.want)
		}
	}
}

func TestCacheControlFor(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{"seg_000.ts", "public, max-age=31536000, immutable"},
		{"index.m3u8", "max-age=60"},
		{"default.jpg", ""},
	}
	for _, tt := range tests {
		if got := CacheControlFor(tt.name); got != tt.want {
			t.Errorf("CacheControlFor(%q) = %q, want %q", tt.name, got, tt.want)
		}
	}
}
