#!/bin/bash
# android arch linux arm aarch64 部署脚本

#######config########
BUSY_BOX="/data/data/qidizi.shell/files/bin/busybox";
# 安装包下载地址
LINUX_URI="http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz";
# 安装包保存路径
LINUX_IMG="/sdcard/-/linux.img.tar.gz";
# 部署的 linux  根目录路径
CHROOT_DIR="/data/data/qidizi.shell/files/linux";
# 唯一操作用户，建议不用其它用户，因为需要权限等预处理才能正常使用
USER_NAME="root"


function linuxDeploy {
    echo "重新部署linux...";

    if [[ ! -f ${LINUX_IMG} ]]; then
        echo "arch linux 镜像文件 ${CHROOT_DIR} 不存在，请通过其它方式从 ${LINUX_URI} 下载镜像时保存路径请填写  ${LINUX_IMG} ，再重试。";
        exit 2
    fi

    echo "解压 ${LINUX_IMG} 至 ${CHROOT_DIR} ...";
    $BUSY_BOX tar -xpf $LINUX_IMG -C $CHROOT_DIR;
    echo "解压完成"
    echo "部署完成"
    linuxConfig
}

function linuxConfig {
    echo '重新配置...'
    gp=<<EOF
aid_system:1000
aid_radio:1001
aid_bluetooth:1002
aid_graphics:1003
aid_input:1004
aid_audio:1005
aid_camera:1006
aid_log:1007
aid_compass:1008
aid_mount:1009
aid_wifi:1010
aid_adb:1011
aid_install:1012
aid_media:1013
aid_dhcp:1014
aid_sdcard_rw:1015
aid_vpn:1016
aid_keystore:1017
aid_usb:1018
aid_drm:1019
aid_available:1020
aid_gps:1021
aid_media_rw:1023
aid_mtp:1024
aid_drmrpc:1026
aid_nfc:1027
aid_sdcard_r:1028
aid_clat:1029
aid_loop_radio:1030
aid_media_drm:1031
aid_package_info:1032
aid_sdcard_pics:1033
aid_sdcard_av:1034
aid_sdcard_all:1035
aid_logd:1036
aid_shared_relro:1037
aid_shell:2000
aid_cache:2001
aid_diag:2002
aid_net_bt_admin:3001
aid_net_bt:3002
aid_inet:3003
aid_net_raw:3004
aid_net_admin:3005
aid_net_bw_stats:3006
aid_net_bw_acct:3007
aid_net_bt_stack:3008
EOF

    # set min uid and gid
    # 对行用查找，并对匹配进行替换
    sed -i 's/^[#]*UID_MIN.*/UID_MIN 5000/' "${CHROOT_DIR}/etc/login.defs"
    sed -i 's/^[#]*GID_MIN.*/GID_MIN 5000/' "${CHROOT_DIR}/etc/login.defs"
    # add android groups
    local aid uid
    for aid in $gp
    do
        if [[ -z "${aid}" ]];then
            continue;
        fi
        
        #用:分隔的，第一节
        local xname=$(echo ${aid} |${BUSY_BOX} awk -F: '{print $1}')
        #：后节
        local xid=$(echo ${aid} |${BUSY_BOX} awk -F: '{print $2}')
        #把已经存在的替换，注意，如果有修改，会丢失
        sed -i "s/^${xname}:.*/${xname}:x:${xid}:${USER_NAME}/" "${CHROOT_DIR}/etc/group"
        if ! $(grep -q "^${xname}:" "${CHROOT_DIR}/etc/group");then
            #如果没有就末尾加上
            echo "${xname}:x:${xid}:${USER_NAME}" >> "${CHROOT_DIR}/etc/group"
        fi
        if ! $(grep -q "^${xname}:" "${CHROOT_DIR}/etc/passwd"); then
            echo "${xname}:x:${xid}:${xid}::/:/bin/false" >> "${CHROOT_DIR}/etc/passwd"
        fi
        # 给操作用户，目前只用root；像网络等权限
        if ! $(grep -q "^${xname}:.*${USER_NAME}" "${CHROOT_DIR}/etc/group"); then
           sed -i "s|^\(${xname}:.*\)|\1,${USER_NAME}|" "${CHROOT_DIR}/etc/group"
        fi
    done

    if ! $(grep -q "^127.0.0.1" "${CHROOT_DIR}/etc/hosts"); then
        echo '127.0.0.1 l' >> "${CHROOT_DIR}/etc/hosts"
    fi
    
    ${BUSY_BOX} rm -fv ${CHROOT_DIR}/etc/resolv.conf
    # arch linux 这个文件指向不存在文件，需要先删除再创建
    echo "nameserver 114.114.114.114" > ${CHROOT_DIR}/etc/resolv.conf
    
    #修改主机名方便手机上更短
    echo "l" > ${CHROOT_DIR}/etc/hostname
    sed -i "s/[#]*PermitRootLogin.*//" "${CHROOT_DIR}/etc/ssh/sshd_config"
    sed -i "s/[#]*Port.*//" "${CHROOT_DIR}/etc/ssh/sshd_config"
    #这个有多行匹配，先全部删除
    sed -i "s/^[#]*ListenAddress.*$//p" "${CHROOT_DIR}/etc/ssh/sshd_config"
    #删除全部空行
    sed -i "/^\s*$/d" "${CHROOT_DIR}/etc/ssh/sshd_config"
    echo -e "ListenAddress 127.0.0.1\nPort 22\nPermitRootLogin yes" >>  "${CHROOT_DIR}/etc/ssh/sshd_config"
    
    # generate sshd keys
    if [ $(ls "${CHROOT_DIR}/etc/ssh/" | grep -c key) -eq 0 ]; then
        chroot_exec -u root ssh-keygen -A >/dev/null
    fi
    
    echo '配置完成'
    return 0
}

is_mounted()
{
    local mount_point="$1"
    [ -n "$mount_point" ] || return 1
    if $(grep -q " ${mount_point%/} " /proc/mounts); then
        return 0
    else
        return 1
    fi
}

fail2die()
{
    if [ $? -eq 0 ]; then
        if [ -n "$2" ]; then
            echo "$2"
        fi
        return 0
    fi

    if [ -n "$1" ]; then
        echo "$1"
    fi
    exit 1
}

linuxStart()
{
   echo "处理中...";
   echo -n "挂载/proc ... "
   local target="$CHROOT_DIR/proc"
   if ! is_mounted "$target"; then
       mkdirOrDie "$target"
       ${BUSY_BOX} mount -t proc proc "$target"
       fail2die "失败" "成功"
   else
       echo "已挂载"
   fi

   echo -n "挂载/sys ... "
   local target="$CHROOT_DIR/sys"
   if ! is_mounted "$target"; then
       mkdirOrDie "$target"
       ${BUSY_BOX} mount -t sysfs sys "$target"
       fail2die "失败" "成功"
   else
       echo "已挂载"
   fi

   echo  -n "挂载/dev ... "
   local target="$CHROOT_DIR/dev"
   if ! is_mounted "$target"; then
       mkdirOrDie "$target"
       ${BUSY_BOX} mount -o bind /dev "$target"
       fail2die "失败" "成功"
   else
       echo "已挂载"
   fi

   echo -n "挂载/dev/shm ... "
   if ! is_mounted "/dev/shm"; then
       mkdirOrDie /dev/shm
       ${BUSY_BOX} mount -o rw,nosuid,nodev,mode=1777 -t tmpfs tmpfs /dev/shm
       fail2die "失败" "成功"
   else
       echo "已挂载"
   fi

   local target="$CHROOT_DIR/dev/shm"
   echo -n "挂载${target}..."
   if ! is_mounted "$target"; then
       ${BUSY_BOX} mount -o bind /dev/shm "$target"
       fail2die "失败" "成功"
   else
       echo "已挂载"
   fi

   echo -n "挂载/dev/pts ... "
   if ! is_mounted "/dev/pts"; then
       mkdirOrDie /dev/pts
       ${BUSY_BOX} mount -o rw,nosuid,noexec,gid=5,mode=620,ptmxmode=000 -t devpts devpts /dev/pts
       fail2die "失败" "成功"
   else
       echo "已挂载"
   fi

   local target="$CHROOT_DIR/dev/pts"
   echo -n "挂载${target}..."
   if ! is_mounted "$target"; then
       ${BUSY_BOX} mount -o bind /dev/pts "$target"
       fail2die "失败" "成功"
   else
       echo "已挂载"
   fi

   echo -n "ln /dev/fd..."
   if [ ! -e "/dev/fd" ];then
       ln -s /proc/self/fd /dev/
       fail2die "失败" "成功"
   else
       echo "已处理"
   fi


   echo -n "ln /dev/stdin..."
   if [ ! -e "/dev/stdin" ] ;then
       ln -s /proc/self/fd/0 /dev/stdin
       fail2die "失败" "成功"
   else
       echo "已处理"
   fi

   echo -n "ln /dev/stdout..."
   if [ ! -e "/dev/stdout" ];then
       ln -s /proc/self/fd/1 /dev/stdout
       fail2die "失败" "成功"
   else
       echo "已处理"
   fi

   echo -n "ln /dev/stderr..."
   if [ ! -e "/dev/stderr" ];then
       ln -s /proc/self/fd/2 /dev/stderr
       fail2die "失败" "成功"
   else
       echo "已处理"
   fi


   echo -n "ln /dev/tty ... "
   if [ ! -e "/dev/tty0" ]; then
       ln -s /dev/null /dev/tty0
       fail2die "失败" "成功"
   else
       echo "已处理"
   fi



   echo -n "挂载/dev/net/tun ... "
   if [ ! -e "/dev/net/tun" ]; then
       mkdirOrDie  /dev/net
       mknod /dev/net/tun c 10 200
       fail2die "失败" "成功"
   else
       echo "已处理"
   fi



   if multiarch_support;then
       local binfmt_dir="/proc/sys/fs/binfmt_misc"
       echo -n "挂载$binfmt_dir ... "
       if ! is_mounted "$binfmt_dir"; then
           ${BUSY_BOX} mount -t binfmt_misc binfmt_misc "$binfmt_dir"
           fail2die "失败" "成功"
       else
           echo "已处理"
       fi
   fi
   
    echo "准备启动sshd..."
    #启动
    chroot_exec -u root /usr/sbin/sshd
    fail2die "失败" "成功"
    echo '请通过root:root@127.0.0.1:22连接sshd'
    # 目前不清楚除了修改etc文件外还有那里指定运行时名字
    chroot_exec -u root hostname l
    echo "启动完成"
    return 0
}

chroot_exec()
{
    unset TMP TEMP TMPDIR LD_PRELOAD LD_DEBUG
    local path="${PATH}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    if [ "$1" = "-u" ]; then
        local username="$2"
        shift 2
    fi
    

        if [ -n "${username}" ]; then
            if [ $# -gt 0 ]; then
                chroot "${CHROOT_DIR}" /bin/su - ${username} -c "$*"
            else
                chroot "${CHROOT_DIR}" /bin/su - ${username}
            fi
        else
            PATH="${path}" chroot "${CHROOT_DIR}" $*
        fi
  
}

linuxStop()
{
    echo "开始处理... "
    local is_release=0
    echo "尝试列举被使用文件..."
    local lsof_full=$(lsof | ${BUSY_BOX} awk '{print $1}' | grep -c '^lsof')

    if [ "$lsof_full" -eq 0 ]; then
        local pids=$(lsof | grep "${CHROOT_DIR%/}" | $BUSY_BOX awk '{print $1}' | uniq)
    else
        local pids=$(lsof | grep "${CHROOT_DIR%/}" | $BUSY_BOX awk '{print $2}' | uniq)
    fi

    kill_pids $pids;
    fail2die "结束进程失败" "全部linux进程已结束"

    echo "卸载分区... "
    local is_mnt=0
    local mask
    for mask in '.*' '*'
    do
        local parts=$(cat /proc/mounts | $BUSY_BOX awk '{print $2}' | grep "^${CHROOT_DIR%/}/$mask$" | sort -r)
        local part
        for part in $parts
        do
            local part_name=$(echo $part | sed "s|^${CHROOT_DIR%/}/*|/|g")
            echo -n "准备卸载分区$part_name ... "
            for i in 1 2 3
            do
                $BUSY_BOX umount $part && break
                sleep 1
            done
            fail2die "卸载分区失败" "卸载分区成功"
            is_mnt=1
        done
    done

    echo "停止完成"
    return 0
}

kill_pids()
{
    local pids=$(get_pids $*)
    if [ -n "$pids" ]; then
        kill -9 $pids
        return $?
    fi
    return 0
}

get_pids()
{
    local pid pidfile pids
    for pid in $*
    do
        pidfile="$CHROOT_DIR${pid}"
        if [ -e "$pidfile" ]; then
            pid=$(cat "$pidfile")
        fi
        if [ -e "/proc/$pid" ]; then
            pids="$pids $pid"
        fi
    done
    if [ -n "$pids" ]; then
        echo $pids
        return 0
    else
        return 1
    fi
}

selinux_inactive()
{
    if [ -e "/sys/fs/selinux/enforce" ]; then
        return $(cat /sys/fs/selinux/enforce)
    else
        return 0
    fi
}

loop_support()
{
    losetup -f 2&> /dev/null;
    return $?
}

multiarch_support()
{
    if [ -d "/proc/sys/fs/binfmt_misc" ]; then
        return 0
    else
        return 1
    fi
}

linuxStatus()
{
    echo '整理状态信息...'
    local model=$(which getprop > /dev/null && getprop ro.product.model)

    if [ -n "$model" ]; then
        echo "设备:$model"
    fi

    local android=$(which getprop > /dev/null && getprop ro.build.version.release)
    if [ -n "$android" ]; then
        echo "Android 版本: $android"
    fi

    echo "处理器内核:$(uname -m)"
    echo "lunix内核: $(uname -r)"
    echo -n "环境变量：\n $(env)\n\n"
    local mem_total=$(grep ^MemTotal /proc/meminfo | $BUSY_BOX awk '{print $2}')
    let mem_total=$mem_total/1024
    local mem_free=$(grep ^MemFree /proc/meminfo | $BUSY_BOX awk '{print $2}')
    let mem_free=$mem_free/1024
    echo "可用/总内存：$mem_free/$mem_total MB"
    local swap_total=$(grep ^SwapTotal /proc/meminfo | $BUSY_BOX awk '{print $2}')
    let swap_total=$swap_total/1024
    local swap_free=$(grep ^SwapFree /proc/meminfo | $BUSY_BOX awk '{print $2}')
    let swap_free=$swap_free/1024
    echo "可用/总交换空间：$swap_free/$swap_total MB"

    (selinux_inactive && echo "SELinux 未启用") || echo "SELinux 已启用"

    (loop_support && echo "Loop devices: 支持") || echo "Loop devices:不支持"

    (multiarch_support && echo "binfmt_misc:支持") || echo "binfmt_misc:不支持"

    local supported_fs=$(printf '%s ' $(grep -v nodev /proc/filesystems | sort))
    echo "支持文件系统：$supported_fs"

    local linux_version=$([ -r "$CHROOT_DIR/etc/os-release" ] && . "$CHROOT_DIR/etc/os-release"; [ -n "$PRETTY_NAME" ] && echo "$PRETTY_NAME" || echo "未知")
    echo "安装的linux：$linux_version"

    local is_mnt=0
    local item
    for item in $(grep "${CHROOT_DIR%/}" /proc/mounts | $BUSY_BOX awk '{print $2}' | sed "s|${CHROOT_DIR%/}/*|/|g")
    do
        echo "已挂载的linux分区: $item"
        local is_mnt=1
    done
    [ "$is_mnt" -ne 1 ] && echo "未挂载linux分区"

    local is_mountpoints=0
    local mp
    for mp in $(grep -v "${CHROOT_DIR%/}" /proc/mounts | grep ^/ | $BUSY_BOX awk '{print $2":"$3}')
    do
        local part=$(echo $mp | $BUSY_BOX awk -F: '{print $1}')
        local fstype=$(echo $mp | $BUSY_BOX awk -F: '{print $2}')
        local block_size=$(stat -c '%s' -f $part)
        local available=$(stat -c '%a' -f $part | $BUSY_BOX awk '{printf("%.1f",$1*'$block_size'/1024/1024/1024)}')
        local total=$(stat -c '%b' -f $part | $BUSY_BOX awk '{printf("%.1f",$1*'$block_size'/1024/1024/1024)}')
        if [ -n "$available" -a -n "$total" ]; then
            echo "android挂载点 $part  $available/$total GB ($fstype)"
            is_mountpoints=1
        fi
    done
    [ "$is_mountpoints" -ne 1 ] && echo "android未挂载"

    local is_partitions=0
    local dev
    for dev in /sys/block/*/dev
    do
        if [ -f $dev ]; then
            local devname=$(echo $dev | sed -e 's@/dev@@' -e 's@.*/@@')
            [ -e "/dev/$devname" ] && local devpath="/dev/$devname"
            [ -e "/dev/block/$devname" ] && local devpath="/dev/block/$devname"
            [ -n "$devpath" ] && local parts=$(fdisk -l $devpath 2> /dev/null | grep ^/dev/ | $BUSY_BOX awk '{print $1}')
            local part
            for part in $parts
            do
                local size=$(fdisk -l $part 2> /dev/null | grep 'Disk.*bytes' | $BUSY_BOX awk '{ sub(/,/,""); print $3" "$4}')
                local type=$(fdisk -l $devpath 2> /dev/null | grep ^$part | tr -d '*' | $BUSY_BOX awk '{str=$6; for (i=7;i<=10;i++) if ($i!="") str=str" "$i; printf("%s",str)}')
                echo " $part  $size ($type)"
                local is_partitions=1
            done
        fi
    done
    [ "$is_partitions" -ne 1 ] && echo " ...没有可用用户分区"
    
    echo '操作完成'
}

die()
{
    if [[ "0" -ne "$1" ]]; then
        if [[ -n "$2" ]]; then
            echo "$2";
        fi
        exit $1;
    fi
}

mkdirOrDie()
{
    if [[ -d "$1" ]]; then
        return 0;
    fi

    mkdir -p "$1";
    die $? "创建目录 $1 失败";
}

##################################

if [[ "$(uname -m)" != "aarch64" ]]; then
    echo "此脚本只支持 aarch64；当前系统构架是：$(uname -m)";
    exit 1;
fi

umask 0022
unset LANG
mkdirOrDie "$CHROOT_DIR";
# parse command
OPTCMD="$1";
shift

case "$OPTCMD" in
    # 部署
    deploy)
    linuxDeploy;
    ;;

start)
    linuxStart
    ;;
stop)
    linuxStop
    ;;
config)
    linuxConfig
    ;;
status)
    linuxStatus
    ;;
*)
    echo "请指定参数，支持：start stop deploy status";
    ;;
esac

exit 0