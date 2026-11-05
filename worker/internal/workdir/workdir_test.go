package workdir

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestValidateRoot(t *testing.T) {
	allowed := filepath.Join(t.TempDir(), "tvc")
	if err := ValidateRoot(allowed); err != nil {
		t.Errorf("root hợp lệ bị từ chối: %v", err)
	}
	if home, err := os.UserHomeDir(); err == nil {
		if err := ValidateRoot(home); err == nil {
			t.Errorf("home %q phải bị từ chối", home)
		}
	}
	tests := []string{"", "tmp/tvc", "/", "/tmp", "/var/tmp", "/Users", "/var", "/private"}
	for _, root := range tests {
		t.Run(root, func(t *testing.T) {
			if err := ValidateRoot(root); err == nil {
				t.Errorf("mong từ chối %q", root)
			}
		})
	}
}

func TestPrepareFreshAndRepeated(t *testing.T) {
	root := filepath.Join(t.TempDir(), "tvc")
	if err := Prepare(root); err != nil {
		t.Fatalf("Prepare fresh: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, markerName)); err != nil {
		t.Fatalf("thiếu marker: %v", err)
	}
	if err := Prepare(root); err != nil {
		t.Fatalf("Prepare lần 2: %v", err)
	}
}

func TestPrepareRemovesOrphansWithMarker(t *testing.T) {
	root := filepath.Join(t.TempDir(), "tvc")
	if err := Prepare(root); err != nil {
		t.Fatalf("Prepare: %v", err)
	}
	orphanDir := filepath.Join(root, "job_dead")
	if err := os.MkdirAll(orphanDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(orphanDir, "source.mp4"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "stray.tmp"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := Prepare(root); err != nil {
		t.Fatalf("Prepare cleanup: %v", err)
	}
	if _, err := os.Stat(orphanDir); !os.IsNotExist(err) {
		t.Errorf("thư mục mồ côi chưa bị xóa: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, "stray.tmp")); !os.IsNotExist(err) {
		t.Errorf("file mồ côi chưa bị xóa: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, markerName)); err != nil {
		t.Errorf("marker bị xóa: %v", err)
	}
}

func TestPrepareRefusesForeignDirectory(t *testing.T) {
	root := filepath.Join(t.TempDir(), "shared")
	if err := os.MkdirAll(root, 0o755); err != nil {
		t.Fatal(err)
	}
	keep := filepath.Join(root, "keep.txt")
	if err := os.WriteFile(keep, []byte("không phải của worker"), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := Prepare(root); err == nil {
		t.Fatal("mong từ chối thư mục lạ không rỗng")
	}
	if _, err := os.Stat(keep); err != nil {
		t.Errorf("không được xóa dữ liệu lạ: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, markerName)); !os.IsNotExist(err) {
		t.Error("không được tạo marker khi từ chối")
	}
}

func TestValidateJobID(t *testing.T) {
	valid := []string{"job_9f2c1b", "job-1", "job.2", "AbC123", strings.Repeat("a", 128)}
	for _, id := range valid {
		if err := ValidateJobID(id); err != nil {
			t.Errorf("id hợp lệ %q bị từ chối: %v", id, err)
		}
	}
	invalid := []string{"", ".", "..", "../x", "a/b", `a\b`, ".hidden", "job id", strings.Repeat("a", 129)}
	for _, id := range invalid {
		t.Run(id, func(t *testing.T) {
			if err := ValidateJobID(id); err == nil {
				t.Errorf("mong từ chối %q", id)
			}
		})
	}
}

func TestPathStaysInsideRoot(t *testing.T) {
	root := filepath.Join(t.TempDir(), "tvc")
	p, err := Path(root, "job_1")
	if err != nil {
		t.Fatalf("Path: %v", err)
	}
	if filepath.Dir(p) != root {
		t.Errorf("Path = %q, không nằm trong %q", p, root)
	}
	if _, err := Path(root, "../escape"); err == nil {
		t.Error("mong từ chối jobId thoát root")
	}
}
