#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TinaIDE 国际化检查工具
用于检测 Kotlin 代码中的硬编码中文字符串
"""

import re
import os
import sys
from pathlib import Path
from typing import List, Dict, Tuple
import json

class I18nChecker:
    def __init__(self, root_dir: str):
        self.root_dir = Path(root_dir)
        self.results = []
        
        # 排除的目录
        self.exclude_dirs = {
            'build', 'test', 'androidTest', '.gradle', '.idea', 
            'external', 'temp'
        }
        
        # 排除的文件模式
        self.exclude_patterns = [
            r'.*Test\.kt$',
            r'.*TestCase\.kt$',
        ]
        
    def should_skip_file(self, file_path: Path) -> bool:
        """判断是否应该跳过该文件"""
        # 检查是否在排除目录中
        for part in file_path.parts:
            if part in self.exclude_dirs:
                return True
        
        # 检查文件名模式
        for pattern in self.exclude_patterns:
            if re.match(pattern, file_path.name):
                return True
                
        return False
    
    def find_chinese_strings(self, file_path: Path) -> List[Dict]:
        """在文件中查找硬编码的中文字符串"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
        except Exception as e:
            print(f"Error reading {file_path}: {e}", file=sys.stderr)
            return []
        
        findings = []
        
        for line_num, line in enumerate(lines, 1):
            # 跳过注释行（简单判断）
            stripped = line.strip()
            if stripped.startswith('//') or stripped.startswith('*'):
                continue
            
            # 查找字符串中的中文
            # 匹配 "..." 或 '...' 中包含中文的情况
            patterns = [
                r'"([^"]*[\u4e00-\u9fff]+[^"]*)"',  # 双引号字符串
                r"'([^']*[\u4e00-\u9fff]+[^']*)'",  # 单引号字符串
            ]
            
            for pattern in patterns:
                matches = re.finditer(pattern, line)
                for match in matches:
                    chinese_text = match.group(1)
                    
                    # 跳过一些特殊情况
                    if self._should_skip_string(chinese_text, line):
                        continue
                    
                    findings.append({
                        'file': str(file_path.relative_to(self.root_dir)),
                        'line': line_num,
                        'text': chinese_text,
                        'context': line.strip()
                    })
        
        return findings
    
    def _should_skip_string(self, text: str, line: str) -> bool:
        """判断是否应该跳过该字符串"""
        # 跳过 Log/Timber 日志（可选，根据需求调整）
        if any(keyword in line for keyword in ['Timber.', 'Log.d', 'Log.e', 'Log.w', 'Log.i']):
            # 如果你想检查日志中的中文，注释掉这一行
            return True
        
        # 跳过注释中的字符串
        if '//' in line and line.index('//') < line.index(text):
            return True
        
        # 跳过 @param、@return 等文档注释
        if any(keyword in line for keyword in ['@param', '@return', '@throws', '@see']):
            return True
            
        return False
    
    def scan_directory(self, directory: Path = None):
        """扫描目录中的所有 Kotlin 文件"""
        if directory is None:
            directory = self.root_dir
        
        kt_files = directory.rglob('*.kt')
        
        for kt_file in kt_files:
            if self.should_skip_file(kt_file):
                continue
            
            findings = self.find_chinese_strings(kt_file)
            self.results.extend(findings)
    
    def generate_report(self, output_format: str = 'text') -> str:
        """生成报告"""
        if output_format == 'json':
            return json.dumps(self.results, ensure_ascii=False, indent=2)
        
        # 文本格式报告
        report_lines = []
        report_lines.append("=" * 80)
        report_lines.append("TinaIDE 国际化检查报告")
        report_lines.append("=" * 80)
        report_lines.append(f"\n总共发现 {len(self.results)} 处硬编码中文字符串\n")
        
        # 按文件分组
        files_dict = {}
        for result in self.results:
            file_path = result['file']
            if file_path not in files_dict:
                files_dict[file_path] = []
            files_dict[file_path].append(result)
        
        # 生成每个文件的报告
        for file_path in sorted(files_dict.keys()):
            findings = files_dict[file_path]
            report_lines.append(f"\n📁 {file_path} ({len(findings)} 处)")
            report_lines.append("-" * 80)
            
            for finding in findings:
                report_lines.append(f"  行 {finding['line']:4d}: {finding['text']}")
                report_lines.append(f"           {finding['context']}")
                report_lines.append("")
        
        # 统计信息
        report_lines.append("\n" + "=" * 80)
        report_lines.append("统计信息")
        report_lines.append("=" * 80)
        report_lines.append(f"受影响文件数: {len(files_dict)}")
        report_lines.append(f"硬编码字符串总数: {len(self.results)}")
        
        # 按文件排序，显示前10个最需要处理的文件
        top_files = sorted(files_dict.items(), key=lambda x: len(x[1]), reverse=True)[:10]
        report_lines.append("\n最需要处理的文件 (Top 10):")
        for i, (file_path, findings) in enumerate(top_files, 1):
            report_lines.append(f"  {i:2d}. {file_path}: {len(findings)} 处")
        
        return '\n'.join(report_lines)
    
    def save_report(self, output_file: str, output_format: str = 'text'):
        """保存报告到文件"""
        report = self.generate_report(output_format)
        
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(report)
        
        print(f"报告已保存到: {output_file}")


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description='检测 TinaIDE 项目中的硬编码中文字符串'
    )
    parser.add_argument(
        'directory',
        nargs='?',
        default='app/src/main/java',
        help='要扫描的目录 (默认: app/src/main/java)'
    )
    parser.add_argument(
        '-o', '--output',
        help='输出报告文件路径'
    )
    parser.add_argument(
        '-f', '--format',
        choices=['text', 'json'],
        default='text',
        help='输出格式 (默认: text)'
    )
    parser.add_argument(
        '--include-logs',
        action='store_true',
        help='包含日志中的中文字符串'
    )
    
    args = parser.parse_args()
    
    # 创建检查器
    checker = I18nChecker(args.directory)
    
    print(f"正在扫描 {args.directory} ...")
    checker.scan_directory()
    
    # 生成报告
    if args.output:
        checker.save_report(args.output, args.format)
    else:
        # 直接打印到控制台
        print(checker.generate_report(args.format))


if __name__ == '__main__':
    main()
