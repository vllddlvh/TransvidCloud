// Package storage thao tác S3 theo docs/contracts/s3-key-convention.md và
// docs-worker/5. Contact docs.md mục 5.3: download input, upload cả cây output
// đúng Content-Type và Cache-Control.
package storage

import (
	"context"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

type Storage struct {
	client *s3.Client
	input  string
	output string
}

func New(client *s3.Client, inputBucket, outputBucket string) *Storage {
	return &Storage{client: client, input: inputBucket, output: outputBucket}
}

// DownloadToFile tải object về dst (tạo thư mục cha nếu thiếu).
func (s *Storage) DownloadToFile(ctx context.Context, key, dst string) error {
	out, err := s.client.GetObject(ctx, &s3.GetObjectInput{
		Bucket: aws.String(s.input),
		Key:    aws.String(key),
	})
	if err != nil {
		return fmt.Errorf("S3_GET_FAILED %s: %w", key, err)
	}
	defer out.Body.Close()

	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return err
	}
	f, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer f.Close()
	if _, err := io.Copy(f, out.Body); err != nil {
		return fmt.Errorf("ghi %s: %w", dst, err)
	}
	return nil
}

// UploadDir PUT mọi file dưới dir lên output bucket với key = keyPrefix + đường
// dẫn tương đối. Trả số object và tổng byte đã đẩy.
func (s *Storage) UploadDir(ctx context.Context, keyPrefix, dir string) (int, int64, error) {
	objects := 0
	var bytesOut int64
	err := filepath.WalkDir(dir, func(p string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}
		rel, err := filepath.Rel(dir, p)
		if err != nil {
			return err
		}
		f, err := os.Open(p)
		if err != nil {
			return err
		}
		defer f.Close()

		key := path.Join(keyPrefix, filepath.ToSlash(rel))
		input := &s3.PutObjectInput{
			Bucket:      aws.String(s.output),
			Key:         aws.String(key),
			Body:        f,
			ContentType: aws.String(ContentTypeFor(rel)),
		}
		if cc := CacheControlFor(rel); cc != "" {
			input.CacheControl = aws.String(cc)
		}
		if _, err := s.client.PutObject(ctx, input); err != nil {
			return fmt.Errorf("S3_PUT_FAILED %s: %w", key, err)
		}
		info, _ := d.Info()
		objects++
		bytesOut += info.Size()
		return nil
	})
	return objects, bytesOut, err
}

// ContentTypeFor ánh xạ đuôi file -> Content-Type (mục 5.3).
func ContentTypeFor(name string) string {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".m3u8":
		return "application/vnd.apple.mpegurl"
	case ".ts":
		return "video/mp2t"
	case ".jpg", ".jpeg":
		return "image/jpeg"
	default:
		return "application/octet-stream"
	}
}

// CacheControlFor: segment .ts ổn định thì immutable; playlist .m3u8 ngắn hạn.
func CacheControlFor(name string) string {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".ts":
		return "public, max-age=31536000, immutable"
	case ".m3u8":
		return "max-age=60"
	default:
		return ""
	}
}
