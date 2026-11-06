from django import forms
from django.contrib.auth.models import User
from .models import UserProfile, StatusPost, ScrapbookMessage, Community, CommunityPost, PhotoAlbum, Photo, Report

class UserRegistrationForm(forms.ModelForm):
    full_name = forms.CharField(max_length=150, required=True, widget=forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Full Name'}))
    username = forms.CharField(max_length=150, required=True, widget=forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Username'}))
    email = forms.EmailField(required=True, widget=forms.EmailInput(attrs={'class': 'form-control', 'placeholder': 'Email Address'}))
    mobile_number = forms.CharField(max_length=20, required=False, widget=forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Mobile Number'}))
    password = forms.CharField(widget=forms.PasswordInput(attrs={'class': 'form-control', 'placeholder': 'Password'}), min_length=6)
    confirm_password = forms.CharField(widget=forms.PasswordInput(attrs={'class': 'form-control', 'placeholder': 'Confirm Password'}))
    gender = forms.ChoiceField(choices=UserProfile.GENDER_CHOICES, widget=forms.Select(attrs={'class': 'form-select'}))
    date_of_birth = forms.DateField(required=False, widget=forms.DateInput(attrs={'class': 'form-control', 'type': 'date'}))
    city = forms.CharField(max_length=100, required=False, widget=forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'City'}))
    profile_photo = forms.ImageField(required=False, widget=forms.FileInput(attrs={'class': 'form-control'}))

    class Meta:
        model = User
        fields = ['username', 'email']

    def clean_username(self):
        username = self.cleaned_data.get('username')
        if User.objects.filter(username__iexact=username).exists():
            raise forms.ValidationError("Username is already taken.")
        return username

    def clean_email(self):
        email = self.cleaned_data.get('email')
        if User.objects.filter(email__iexact=email).exists():
            raise forms.ValidationError("Email address is already registered.")
        return email

    def clean(self):
        cleaned_data = super().clean()
        password = cleaned_data.get('password')
        confirm_password = cleaned_data.get('confirm_password')
        if password and confirm_password and password != confirm_password:
            self.add_error('confirm_password', "Passwords do not match.")
        return cleaned_data


class UserProfileForm(forms.ModelForm):
    class Meta:
        model = UserProfile
        fields = [
            'full_name', 'mobile_number', 'gender', 'date_of_birth',
            'city', 'country', 'profile_photo', 'cover_photo',
            'about_me', 'relationship_status', 'interests_hobbies',
            'education', 'profession', 'fortune', 'scrapbook_privacy'
        ]
        widgets = {
            'full_name': forms.TextInput(attrs={'class': 'form-control'}),
            'mobile_number': forms.TextInput(attrs={'class': 'form-control'}),
            'gender': forms.Select(attrs={'class': 'form-select'}),
            'date_of_birth': forms.DateInput(attrs={'class': 'form-control', 'type': 'date'}),
            'city': forms.TextInput(attrs={'class': 'form-control'}),
            'country': forms.TextInput(attrs={'class': 'form-control'}),
            'profile_photo': forms.FileInput(attrs={'class': 'form-control'}),
            'cover_photo': forms.FileInput(attrs={'class': 'form-control'}),
            'about_me': forms.Textarea(attrs={'class': 'form-control', 'rows': 3}),
            'relationship_status': forms.Select(attrs={'class': 'form-select'}),
            'interests_hobbies': forms.Textarea(attrs={'class': 'form-control', 'rows': 2}),
            'education': forms.TextInput(attrs={'class': 'form-control'}),
            'profession': forms.TextInput(attrs={'class': 'form-control'}),
            'fortune': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Today\'s status or fortune'}),
            'scrapbook_privacy': forms.Select(attrs={'class': 'form-select'}),
        }


class StatusPostForm(forms.ModelForm):
    class Meta:
        model = StatusPost
        fields = ['content', 'image', 'link_url']
        widgets = {
            'content': forms.Textarea(attrs={'class': 'form-control', 'rows': 3, 'placeholder': 'What\'s on your mind? Share an update with your friends...'}),
            'image': forms.FileInput(attrs={'class': 'form-control'}),
            'link_url': forms.URLInput(attrs={'class': 'form-control', 'placeholder': 'https://example.com (optional link)'}),
        }


class ScrapForm(forms.ModelForm):
    class Meta:
        model = ScrapbookMessage
        fields = ['message']
        widgets = {
            'message': forms.Textarea(attrs={'class': 'form-control', 'rows': 3, 'placeholder': 'Leave a scrap message here... (e.g. Hi buddy! Long time no see!)'}),
        }


class CommunityForm(forms.ModelForm):
    class Meta:
        model = Community
        fields = ['name', 'description', 'category', 'profile_picture', 'is_private']
        widgets = {
            'name': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Community Name (e.g. Web Developers United)'}),
            'description': forms.Textarea(attrs={'class': 'form-control', 'rows': 3, 'placeholder': 'Tell people what this community is all about...'}),
            'category': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Category (e.g. Technology, Music, Movies)'}),
            'profile_picture': forms.FileInput(attrs={'class': 'form-control'}),
            'is_private': forms.CheckboxInput(attrs={'class': 'form-check-input'}),
        }


class CommunityPostForm(forms.ModelForm):
    class Meta:
        model = CommunityPost
        fields = ['title', 'content']
        widgets = {
            'title': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Topic Title'}),
            'content': forms.Textarea(attrs={'class': 'form-control', 'rows': 4, 'placeholder': 'Start a discussion...'}),
        }


class PhotoAlbumForm(forms.ModelForm):
    class Meta:
        model = PhotoAlbum
        fields = ['title', 'description', 'is_private']
        widgets = {
            'title': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Album Title (e.g. Vacation 2026)'}),
            'description': forms.Textarea(attrs={'class': 'form-control', 'rows': 2, 'placeholder': 'Album Description...'}),
            'is_private': forms.CheckboxInput(attrs={'class': 'form-check-input'}),
        }


class PhotoUploadForm(forms.ModelForm):
    class Meta:
        model = Photo
        fields = ['album', 'image', 'caption']
        widgets = {
            'album': forms.Select(attrs={'class': 'form-select'}),
            'image': forms.FileInput(attrs={'class': 'form-control'}),
            'caption': forms.TextInput(attrs={'class': 'form-control', 'placeholder': 'Photo caption...'}),
        }


class ReportForm(forms.ModelForm):
    class Meta:
        model = Report
        fields = ['reason']
        widgets = {
            'reason': forms.Textarea(attrs={'class': 'form-control', 'rows': 3, 'placeholder': 'Please specify why you are reporting this content...'}),
        }
