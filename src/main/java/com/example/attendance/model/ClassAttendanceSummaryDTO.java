package com.example.attendance.model;

import java.util.List;

public class ClassAttendanceSummaryDTO {

    private Long classId;
    private String className;
    private int totalStudents;
    private double overallClassAveragePercentage;
    private List<ClassSubjectAverageDTO> subjectAverages;

    public ClassAttendanceSummaryDTO() {
    }

    public ClassAttendanceSummaryDTO(Long classId, String className, int totalStudents,
                                     double overallClassAveragePercentage,
                                     List<ClassSubjectAverageDTO> subjectAverages) {
        this.classId = classId;
        this.className = className;
        this.totalStudents = totalStudents;
        this.overallClassAveragePercentage = overallClassAveragePercentage;
        this.subjectAverages = subjectAverages;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    public double getOverallClassAveragePercentage() {
        return overallClassAveragePercentage;
    }

    public void setOverallClassAveragePercentage(double overallClassAveragePercentage) {
        this.overallClassAveragePercentage = overallClassAveragePercentage;
    }

    public List<ClassSubjectAverageDTO> getSubjectAverages() {
        return subjectAverages;
    }

    public void setSubjectAverages(List<ClassSubjectAverageDTO> subjectAverages) {
        this.subjectAverages = subjectAverages;
    }

    public static class ClassSubjectAverageDTO {
        private String subject;
        private long totalSessionsHeld;
        private double averagePercentage;
        private int studentCount;
        private long totalPresentCheckins;
        private long totalAbsences;

        public ClassSubjectAverageDTO() {
        }

        public ClassSubjectAverageDTO(String subject, long totalSessionsHeld, double averagePercentage, int studentCount) {
            this.subject = subject;
            this.totalSessionsHeld = totalSessionsHeld;
            this.averagePercentage = averagePercentage;
            this.studentCount = studentCount;
        }

        public ClassSubjectAverageDTO(String subject, long totalSessionsHeld, double averagePercentage, int studentCount, long totalPresentCheckins, long totalAbsences) {
            this.subject = subject;
            this.totalSessionsHeld = totalSessionsHeld;
            this.averagePercentage = averagePercentage;
            this.studentCount = studentCount;
            this.totalPresentCheckins = totalPresentCheckins;
            this.totalAbsences = totalAbsences;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public long getTotalSessionsHeld() {
            return totalSessionsHeld;
        }

        public void setTotalSessionsHeld(long totalSessionsHeld) {
            this.totalSessionsHeld = totalSessionsHeld;
        }

        public double getAveragePercentage() {
            return averagePercentage;
        }

        public void setAveragePercentage(double averagePercentage) {
            this.averagePercentage = averagePercentage;
        }

        public int getStudentCount() {
            return studentCount;
        }

        public void setStudentCount(int studentCount) {
            this.studentCount = studentCount;
        }

        public long getTotalPresentCheckins() {
            return totalPresentCheckins;
        }

        public void setTotalPresentCheckins(long totalPresentCheckins) {
            this.totalPresentCheckins = totalPresentCheckins;
        }

        public long getTotalAbsences() {
            return totalAbsences;
        }

        public void setTotalAbsences(long totalAbsences) {
            this.totalAbsences = totalAbsences;
        }
    }
}
