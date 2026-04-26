<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>在线图片压缩工具</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 20px;
        }

        .container {
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            padding: 40px;
            max-width: 600px;
            width: 100%;
        }

        h1 {
            text-align: center;
            color: #333;
            margin-bottom: 10px;
            font-size: 28px;
        }

        .subtitle {
            text-align: center;
            color: #666;
            margin-bottom: 30px;
            font-size: 14px;
        }

        .upload-area {
            border: 3px dashed #667eea;
            border-radius: 15px;
            padding: 50px 30px;
            text-align: center;
            transition: all 0.3s ease;
            cursor: pointer;
            position: relative;
            background: #f8f9ff;
        }

        .upload-area:hover {
            border-color: #764ba2;
            background: #f0f2ff;
        }

        .upload-area.dragover {
            border-color: #764ba2;
            background: #e8ebff;
            transform: scale(1.02);
        }

        .upload-icon {
            width: 80px;
            height: 80px;
            margin-bottom: 20px;
        }

        .upload-text {
            color: #333;
            font-size: 18px;
            font-weight: 600;
            margin-bottom: 10px;
        }

        .upload-hint {
            color: #888;
            font-size: 14px;
            margin-bottom: 20px;
        }

        .supported-formats {
            color: #667eea;
            font-size: 13px;
            font-weight: 500;
        }

        #imageFile {
            position: absolute;
            width: 100%;
            height: 100%;
            top: 0;
            left: 0;
            opacity: 0;
            cursor: pointer;
        }

        .file-info {
            margin-top: 20px;
            padding: 15px;
            background: #f0f4ff;
            border-radius: 10px;
            display: none;
        }

        .file-info.visible {
            display: block;
        }

        .file-name {
            color: #333;
            font-weight: 600;
            margin-bottom: 5px;
        }

        .file-size {
            color: #666;
            font-size: 14px;
        }

        .submit-btn {
            width: 100%;
            padding: 15px 30px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 10px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s ease;
            margin-top: 20px;
            display: none;
        }

        .submit-btn.visible {
            display: block;
        }

        .submit-btn:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
        }

        .submit-btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

        .error-message {
            background: #fff2f2;
            border: 1px solid #ffcccc;
            color: #cc0000;
            padding: 15px;
            border-radius: 10px;
            margin-bottom: 20px;
            display: none;
        }

        .error-message.visible {
            display: block;
        }

        .loading-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.7);
            display: none;
            justify-content: center;
            align-items: center;
            z-index: 9999;
        }

        .loading-overlay.active {
            display: flex;
        }

        .loading-content {
            background: white;
            border-radius: 20px;
            padding: 40px;
            text-align: center;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
        }

        .spinner {
            width: 60px;
            height: 60px;
            border: 5px solid #f3f3f3;
            border-top: 5px solid #667eea;
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin: 0 auto 20px;
        }

        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        .loading-text {
            color: #333;
            font-size: 18px;
            font-weight: 600;
            margin-bottom: 10px;
        }

        .loading-hint {
            color: #888;
            font-size: 14px;
        }

        .success-message {
            background: #f0fff4;
            border: 1px solid #9ae6b4;
            color: #22543d;
            padding: 15px;
            border-radius: 10px;
            margin-bottom: 20px;
            display: none;
        }

        .success-message.visible {
            display: block;
        }

        .size-info {
            display: flex;
            justify-content: space-between;
            margin-top: 10px;
            font-size: 14px;
            color: #666;
        }

        .compress-ratio {
            color: #667eea;
            font-weight: 600;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>在线图片压缩工具</h1>
        <p class="subtitle">快速、免费、安全地压缩您的图片</p>

        <#if error??>
            <div class="error-message visible">${error}</div>
        </#if>

        <form id="uploadForm" action="/compress" method="post" enctype="multipart/form-data">
            <div class="upload-area" id="uploadArea">
                <svg class="upload-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="#667eea" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M17 8L12 3L7 8" stroke="#667eea" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M12 3V15" stroke="#667eea" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <div class="upload-text">点击或拖拽图片到此处</div>
                <div class="upload-hint">支持 PNG、JPG/JPEG、WebP、GIF 格式</div>
                <div class="supported-formats">最大支持 150MB 的图片文件</div>
                <input type="file" id="imageFile" name="imageFile" accept=".png,.jpg,.jpeg,.webp,.gif" required>
            </div>

            <div class="file-info" id="fileInfo">
                <div class="file-name" id="fileName"></div>
                <div class="file-size" id="fileSize"></div>
            </div>

            <button type="submit" class="submit-btn" id="submitBtn">开始压缩</button>
        </form>

        <div class="success-message" id="successMessage">
            压缩成功！文件已自动下载。
        </div>
    </div>

    <div class="loading-overlay" id="loadingOverlay">
        <div class="loading-content">
            <div class="spinner"></div>
            <div class="loading-text">正在压缩图片...</div>
            <div class="loading-hint">请稍候，大文件可能需要较长时间</div>
        </div>
    </div>

    <script>
        const uploadArea = document.getElementById('uploadArea');
        const imageFile = document.getElementById('imageFile');
        const fileInfo = document.getElementById('fileInfo');
        const fileName = document.getElementById('fileName');
        const fileSize = document.getElementById('fileSize');
        const submitBtn = document.getElementById('submitBtn');
        const uploadForm = document.getElementById('uploadForm');
        const loadingOverlay = document.getElementById('loadingOverlay');
        const successMessage = document.getElementById('successMessage');
        const errorMessage = document.querySelector('.error-message');

        imageFile.addEventListener('change', function(e) {
            const file = e.target.files[0];
            if (file) {
                handleFileSelection(file);
            }
        });

        uploadArea.addEventListener('dragover', function(e) {
            e.preventDefault();
            uploadArea.classList.add('dragover');
        });

        uploadArea.addEventListener('dragleave', function(e) {
            e.preventDefault();
            uploadArea.classList.remove('dragover');
        });

        uploadArea.addEventListener('drop', function(e) {
            e.preventDefault();
            uploadArea.classList.remove('dragover');
            
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                const file = files[0];
                if (isValidFileType(file)) {
                    imageFile.files = files;
                    handleFileSelection(file);
                } else {
                    showError('不支持的文件格式，请上传 PNG、JPG/JPEG、WebP 或 GIF 格式的图片');
                }
            }
        });

        function isValidFileType(file) {
            const validTypes = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
            const validExtensions = ['.png', '.jpg', '.jpeg', '.webp', '.gif'];
            
            if (validTypes.includes(file.type)) {
                return true;
            }
            
            const extension = '.' + file.name.split('.').pop().toLowerCase();
            return validExtensions.includes(extension);
        }

        function handleFileSelection(file) {
            if (!isValidFileType(file)) {
                showError('不支持的文件格式，请上传 PNG、JPG/JPEG、WebP 或 GIF 格式的图片');
                resetForm();
                return;
            }

            if (file.size > 150 * 1024 * 1024) {
                showError('文件大小超过 150MB 限制');
                resetForm();
                return;
            }

            fileName.textContent = '文件名: ' + file.name;
            fileSize.textContent = '文件大小: ' + formatFileSize(file.size);
            fileInfo.classList.add('visible');
            submitBtn.classList.add('visible');
            hideError();
            hideSuccess();
        }

        function formatFileSize(bytes) {
            if (bytes === 0) return '0 Bytes';
            const k = 1024;
            const sizes = ['Bytes', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        }

        function showError(message) {
            if (errorMessage) {
                errorMessage.textContent = message;
                errorMessage.classList.add('visible');
            }
        }

        function hideError() {
            if (errorMessage) {
                errorMessage.classList.remove('visible');
            }
        }

        function hideSuccess() {
            successMessage.classList.remove('visible');
        }

        function resetForm() {
            imageFile.value = '';
            fileInfo.classList.remove('visible');
            submitBtn.classList.remove('visible');
        }

        uploadForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const file = imageFile.files[0];
            if (!file) {
                showError('请选择要上传的图片文件');
                return;
            }

            submitBtn.disabled = true;
            loadingOverlay.classList.add('active');
            hideError();
            hideSuccess();

            const formData = new FormData();
            formData.append('imageFile', file);

            try {
                const response = await fetch('/compress', {
                    method: 'POST',
                    body: formData
                });

                if (!response.ok) {
                    const errorText = await response.text();
                    throw new Error(errorText || '压缩失败，请重试');
                }

                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = file.name;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);

                successMessage.classList.add('visible');
                resetForm();

            } catch (error) {
                console.error('Error:', error);
                showError(error.message || '压缩失败，请稍后重试');
            } finally {
                submitBtn.disabled = false;
                loadingOverlay.classList.remove('active');
            }
        });
    </script>
</body>
</html>
