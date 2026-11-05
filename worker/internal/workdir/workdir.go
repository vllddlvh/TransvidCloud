// Package workdir quản lý vùng thư mục tạm của worker: chỉ dọn thư mục con
// bên trong root do chính worker tạo (có marker), và chặn jobId thoát khỏi root.
package workdir

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const markerName = ".tvc-worker-tmp"

var jobIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`)

// Gốc dùng chung hoặc quá nông: dọn "mọi thứ bên trong" sẽ phá dữ liệu khác.
var deniedRoots = map[string]bool{
	"/": true, "/tmp": true, "/var": true, "/var/tmp": true,
	"/private": true, "/private/tmp": true, "/Users": true,
}

// ValidateRoot từ chối cấu hình TVC_TMP_DIR nguy hiểm.
func ValidateRoot(root string) error {
	if root == "" {
		return errors.New("TVC_TMP_DIR không được rỗng")
	}
	if !filepath.IsAbs(root) {
		return fmt.Errorf("TVC_TMP_DIR phải là đường dẫn tuyệt đối, nhận %q", root)
	}
	clean := filepath.Clean(root)
	if clean == string(os.PathSeparator) || deniedRoots[clean] {
		return fmt.Errorf("TVC_TMP_DIR %q là thư mục dùng chung, không được dùng làm gốc tạm", root)
	}
	if home, err := os.UserHomeDir(); err == nil && clean == filepath.Clean(home) {
		return fmt.Errorf("TVC_TMP_DIR %q là thư mục home, không được dùng làm gốc tạm", root)
	}
	if strings.Count(clean, string(os.PathSeparator)) < 2 {
		return fmt.Errorf("TVC_TMP_DIR %q quá nông, cần thư mục con riêng (vd /tmp/tvc)", root)
	}
	return nil
}

// Prepare tạo root nếu thiếu và dọn các thư mục job mồ côi từ lần chạy trước.
// Chỉ dọn khi root có marker của worker; root lạ không rỗng thì từ chối khởi động.
func Prepare(root string) error {
	if err := ValidateRoot(root); err != nil {
		return err
	}
	if err := os.MkdirAll(root, 0o755); err != nil {
		return fmt.Errorf("tạo thư mục tạm %s: %w", root, err)
	}
	marker := filepath.Join(root, markerName)
	if _, err := os.Stat(marker); err != nil {
		if !errors.Is(err, fs.ErrNotExist) {
			return fmt.Errorf("kiểm tra marker %s: %w", marker, err)
		}
		entries, err := os.ReadDir(root)
		if err != nil {
			return fmt.Errorf("đọc thư mục tạm %s: %w", root, err)
		}
		if len(entries) > 0 {
			return fmt.Errorf("thư mục tạm %s không rỗng và không có marker %s; "+
				"từ chối dọn dữ liệu không do worker tạo", root, markerName)
		}
		if err := os.WriteFile(marker, []byte("TransvidCloud worker temp dir\n"), 0o644); err != nil {
			return fmt.Errorf("ghi marker %s: %w", marker, err)
		}
		return nil
	}

	entries, err := os.ReadDir(root)
	if err != nil {
		return fmt.Errorf("đọc thư mục tạm %s: %w", root, err)
	}
	for _, e := range entries {
		if e.Name() == markerName {
			continue
		}
		if err := os.RemoveAll(filepath.Join(root, e.Name())); err != nil {
			return fmt.Errorf("dọn %s mồ côi: %w", e.Name(), err)
		}
	}
	return nil
}

// ValidateJobID chặn ID có dấu phân cách/.. dùng làm tên thư mục.
func ValidateJobID(id string) error {
	if !jobIDPattern.MatchString(id) {
		return fmt.Errorf("jobId %q không hợp lệ: chỉ cho phép chữ, số, '.', '_', '-' (tối đa 128 ký tự)", id)
	}
	return nil
}

// Path trả thư mục làm việc của một job, bảo đảm nằm trong root.
func Path(root, jobID string) (string, error) {
	if err := ValidateJobID(jobID); err != nil {
		return "", err
	}
	p := filepath.Join(root, jobID)
	rel, err := filepath.Rel(root, p)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(os.PathSeparator)) {
		return "", fmt.Errorf("jobId %q thoát khỏi thư mục tạm", jobID)
	}
	return p, nil
}
